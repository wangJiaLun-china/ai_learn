---
step: '04'
---

# 04 工具调用

> 分组：基础内核

**Function Calling：给模型装上手脚** —— 模型负责决定「用什么工具、传什么参数」，执行永远在你手里 —— 这是智能体安全性的基石。

## 核心概念 {#concept}

### 为什么「决定权在模型、执行权在你」

::: tip 💡 安全性基石
模型输出的只是一段**文本**（工具名 + 参数），它永远无法直接触达你的数据库或文件系统。所有副作用都发生在你的 `execute()` 里 —— 你可以校验参数、做权限检查、加白名单。这就是 Function Calling 与「让模型直接执行代码」的本质区别。
:::

### 教学版 vs 生产版

|   | 本场景（教学） | 生产协议 |
| --- | --- | --- |
| 工具声明 | 提示词里写清单 | 请求体 `tools` 数组 + JSON Schema |
| 调用表达 | 模型输出 JSON 文本 | 响应 `tool_calls` 结构化字段 |
| 结果回填 | user 消息拼接 | role=`tool` 专用消息 |

形式不同，**语义完全一致**：先决策、再执行、后回填。读懂了教学版，OpenAI / Claude / MCP 的原生协议只是换个「信封格式」。

### 好的工具设计长什么样

- **单一职责**：一个工具只做一件事，复杂动作组合成技能（见 Step10）；
- **参数自描述**：模型只见过 schema，参数名要「望文生义」；
- **失败要可读**：返回「城市未收录」而不是抛堆栈，模型才能自我纠错。

## 实现要点 {#impl}

### 工具调用的三步舞

1. 声明工具 → 2. 模型决策调用 → 3. 执行 + 回填 → 模型生成回答

```java
// 第一步：在提示词中声明工具清单（最简 Tool Schema）
String SYSTEM_PROMPT = """
        你是一个可以调用工具的智能体。可用工具：
        - get_weather(city): 查询指定城市的实时天气
        - calculator(expression): 计算四则运算

        需要工具时，只输出 JSON：
        {"tool": "工具名", "arguments": {"参数名": "值"}}
        不需要工具时，直接自然语言回答。
        """;

// 第二步：模型返回结构化调用请求
String first = model.chat(messages);
// → {"tool": "get_weather", "arguments": {"city": "广州"}}

// 第三步：执行工具，把结果回填
String result = execute(tool, args);
messages.add(Message.user("工具执行结果: " + result));
String answer = model.chat(messages);   // 基于事实作答
```

## ToyAgent vs ToyAgentScope {#compare}

**Function Calling：把循环拆开看** —— 不用 Agent，直接用 Model + Toolkit + ReasoningContext 三块积木走一遍「提议 → 执行 → 回填」。

### 三步走

带 ToolSchema 调模型 → 模型返回 ToolUseBlock → toolkit.callTool 执行 → ToolResultMessage 回填 → 再调模型出答案

```java
// 1. 带着工具 Schema 调模型；流式分片用框架的累加器拼成完整消息
ReasoningContext acc = new ReasoningContext("tool-call");
model.stream(messages, toolkit.getToolSchemas(), GenerateOptions.builder().build())
        .doOnNext(acc::processChunk).blockLast();
Msg first = acc.buildFinalMessage();

// 2. 执行模型提议的调用
for (ToolUseBlock call : first.getContentBlocks(ToolUseBlock.class)) {
    ToolResultBlock r = toolkit.callTool(ToolCallParam.builder()
            .toolUseBlock(call).input(call.getInput()).build()).block();
    messages.add(new ToolResultMessage(call.getId(), call.getName(), Texts.textOf(r)));
}

// 3. 基于工具结果生成最终回答
Msg second = callModel(messages);
```

::: warning ⚠️ 踩坑：手工构造 ToolUseBlock
Toolkit 会用 JSON Schema 校验参数，校验的是 ToolUseBlock 里的**原始 JSON（content）**。从模型返回的调用自带 content；自己 `ToolUseBlock.builder()` 构造时只填 `input(Map)` 会报 “argument content is null” —— 见 `support/ToolCalls.java`，input 与 content 都要填。
:::

## 对应教程 {#chapters}

- ch03 天气查询助手
- ch10 Function Calling 与工具设计
