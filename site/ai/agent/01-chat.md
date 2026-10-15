---
step: '01'
---

# 01 对话

> 分组：基础内核

**对话智能体：一切从一个方法开始** —— 剥掉所有概念外衣，智能体的最小实现是 17 行代码。

## 核心概念 {#concept}

### 为什么这是「最小」MVP

- **无策略**：输入什么就转发什么，没有任何预处理；
- **无状态**：每一轮对话互相独立，模型「失忆」；
- **无工具**：模型只能用参数里的知识回答，问天气它只能瞎猜。

接下来的 11 个场景，每场景只往这个骨架里**加一种能力** —— 读懂了这里，就读懂了智能体的一半。

## 实现要点 {#impl}

### 智能体的最小骨架

网上对 Agent 的定义五花八门，但工程上的起点极其朴素：**把用户的话交给模型，把模型的话还给用户**。

```java
public interface ChatAgent extends Agent {

    class Impl implements ChatAgent {

        private final ChatModel model;

        public Impl(ChatModel model) { this.model = model; }

        @Override
        public String chat(String input) {
            // 全部实现就这一行核心逻辑
            return model.chat(List.of(Message.user(input)));
        }
    }
}
```

这里有一个关键的**抽象分层**：`ChatModel` 接口屏蔽了「模型是谁」。它可能是 OpenAI 兼容端点，也可能是本地 Mock —— 智能体的策略代码完全不用改。

```java
// 模型抽象：智能体的"大脑"
public interface ChatModel {
    String chat(List<Message> messages);
}
```

### 模型调用到底发生了什么

`OpenAiChatModel` 的实现揭开了大模型的"神秘面纱"：所谓调用模型，就是一次 HTTP POST。

```java
POST {base-url}/chat/completions
{
  "model": "deepseek-chat",
  "messages": [{"role": "user", "content": "你好"}]
}
// 响应取 choices[0].message.content
```

::: tip 💡 面试考点
「Agent 和 Chatbot 的区别是什么？」—— 最小 MVP 阶段两者没有区别。区别来自后续能力：Chatbot 停在对话，Agent 在对话之上叠加了**工具、记忆、循环与目标导向**。
:::

## ToyAgent vs ToyAgentScope {#compare}

**对话智能体：一个 ReActAgent，一行 call()** —— 最小 MVP 从「手写 HTTP 调模型」变成「装配一个不带工具的 ReActAgent」。

### 手写版 vs 框架版

|   | ToyAgent | ToyAgentScope |
| --- | --- | --- |
| 调模型 | `model.chat(List.of(Message.user(input)))` | `agent.call(List.of(new UserMessage(input)), ctx)` |
| 模型实现 | 自己拼 /chat/completions 请求 | `OpenAIChatModel`（流式、重试、推理内容解析） |
| 会话 | 无 | 按 (userId, sessionId) 隔离，每次新 sessionId = 无记忆 |

### 核心代码

```java
this.agent = ReActAgent.builder()
        .name("chat")
        .model(model)          // OpenAI 兼容模型，见 llm/Models.java
        .build();

public String chat(String input) {
    // 每次新会话 → 每轮独立、没有记忆
    RuntimeContext ctx = RuntimeContext.builder()
            .userId("toy").sessionId(UUID.randomUUID().toString()).build();
    Msg reply = agent.call(List.of(new UserMessage(input)), ctx).block();
    return reply.getTextContent();
}
```

::: tip 💡 关键概念：智能体无状态
AgentScope 2.0 删除了 1.x 的 Memory 对象。**智能体实例本身不存对话**，会话状态（AgentState）按 `(userId, sessionId)` 存放。同一个实例可以同时服务很多会话 —— 这是生产部署的基础。
:::

### 模型层：一处装配，处处复用

```java
OpenAIChatModel.builder()
        .baseUrl(cfg.baseUrl())     // 以 /v1、/v3 结尾都行，框架会去重版本段
        .apiKey(cfg.apiKey())
        .modelName(cfg.model())
        .stream(true)
        .formatter(new DeepSeekFormatter())   // DeepSeek 系列：正确回传 reasoning_content
        .nativeStructuredOutput(false)        // 结构化输出走工具调用（见 Step06）
        .build();
```

## 对应教程 {#chapters}

- ch01 大模型基础
- ch02 什么是 AI Agent
- ch03 你的第一个 Agent
