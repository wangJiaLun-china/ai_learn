---
step: '03'
---

# 03 ReAct

> 分组：基础内核

**ReAct：让模型学会边想边做** —— Thought → Action → Observation → 循环，直到 Final Answer。几乎所有智能体框架的内核。

## 核心概念 {#concept}

### 模型的两个短板，ReAct 全补上

- **幻觉**：模型会一本正经地编造实时信息（比如天气）；
- **一次成型**：复杂问题需要拆步骤，模型却总想一口气答完。

ReAct（Reasoning + Acting）的做法：让模型**一次只走一小步** —— 先说想法，再说行动，然后停下等人执行，拿到观察结果再继续想。

### 为什么必须设 MAX_STEPS

如果模型思考后再次要求调用同一个工具，或者工具一直返回错误，循环就会无限烧钱。生产系统里这个保险丝叫 **Budget Guard**（步数 / Token / 时间预算），是 Agent Runtime 的标配。

::: info 🚀 动手观察
启动 ToyAgent 网页（`http://localhost:8099`）问「北京今天天气怎么样」，时间线会完整展示 Thought → Action → Observation → Final 四步 —— 你正在亲眼看着一个 ReAct 循环运转。
:::

## 实现要点 {#impl}

### 协议约定：让模型输出 JSON

```java
private static final String SYSTEM_PROMPT = """
        你是一个使用 ReAct 模式解决问题的智能体。
        每一轮只输出一个 JSON 对象，格式二选一：
        1. 继续行动：{"thought": "...", "action": "工具名", "action_input": "参数"}
        2. 给出答案：{"thought": "...", "final": "最终答案"}
        可用工具：
        - get_weather(city): 查询城市天气
        - calculator(expression): 计算四则运算
        """;
```

### 循环体：20 行写完 ReAct

```java
for (int step = 1; step <= MAX_STEPS; step++) {
    String raw = model.chat(messages);          // 1. 模型思考
    Object parsed = Json.parse(raw);

    String fin = Json.str(parsed, "final");
    if (fin != null) return fin;                 // 2. 有答案 → 结束

    String action = Json.str(parsed, "action");  // 3. 有行动 → 执行工具
    String observation = executeTool(action,
            Json.str(parsed, "action_input"));

    messages.add(Message.assistant(raw));        // 4. 结果喂回模型
    messages.add(Message.user("Observation: " + observation));
}
return "已达最大步数，循环终止";                 // 5. 保险丝
```

::: tip 💡 灵魂所在
第 4 步是 ReAct 的精髓：把工具结果作为新消息**追加进对话**再发给模型。模型并没有「感知世界」的能力，它的一切认知都来自上下文 —— 上下文里有什么，它就「活」在什么世界里。
:::

## ToyAgent vs ToyAgentScope {#compare}

**ReAct：框架就是那个循环** —— 手写版 100 行的「思考-行动-观察」循环，在 AgentScope 里就是 ReActAgent 本身；我们只需订阅它的事件流。

### 手写版做了哪些事，框架替你做了哪些

| 环节 | ToyAgent 手写 | AgentScope |
| --- | --- | --- |
| 输出协议 | 提示词约定只输出 JSON | 模型原生 Function Calling |
| 解析 | 去代码围栏 + Json.parse | 框架解析 ToolUseBlock |
| 工具分发 | switch(action) | `Toolkit` 按名反射调用 @Tool 方法 |
| 回填观察 | 拼一条 “Observation: …” 用户消息 | ToolResultBlock 自动写回上下文 |
| 保险丝 | for 循环 MAX_STEPS | `maxIters(5)` + EXCEED_MAX_ITERS 事件 |
| 并行工具 | 不支持 | 一次推理可发起多个调用 |

### 核心代码

```java
// 1. 工具：普通方法 + 注解，框架自动生成 JSON Schema
public class CommonTools {
    @Tool(name = "get_weather", description = "查询城市今天的天气", readOnly = true)
    public String getWeather(@ToolParam(name = "city", description = "城市名") String city) { ... }
}

// 2. 装配
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new CommonTools());
ReActAgent agent = ReActAgent.builder()
        .name("react").model(model).toolkit(toolkit)
        .maxIters(5)                       // 保险丝
        .build();

// 3. 运行 + 把事件流翻译成轨迹
return Tracer.of(log).run(agent.streamEvents(List.of(new UserMessage(input)), ctx)).text();
```

### 事件流 → 轨迹（support/Tracer.java）

| AgentScope 事件 | 轨迹节点 |
| --- | --- |
| THINKING_BLOCK_* / 工具调用前的文本 | thought |
| TOOL_CALL_START / DELTA / END | action：name(args) |
| TOOL_RESULT_TEXT_DELTA / END | observation |
| EXCEED_MAX_ITERS | guard：保险丝熔断 |
| AGENT_RESULT | final |

::: tip 💡 为什么订阅事件而不是 call()
`call()` 只给最终答案；`streamEvents()` 给出每一步的类型化事件 —— 这正是可观测性、前端时间线、审计日志的来源。AgentScope 2.0 的头号新特性就是这套事件系统。
:::

::: info 🧪 试一试
「上海天气如何，适合出行吗，顺便算一下 12*8」—— 观察模型在**同一轮**里并行调用两个工具。
:::

## 对应教程 {#chapters}

- ch04b ReAct 模式
- ch05 Agent 的思考
