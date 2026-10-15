---
step: '06'
---

# 06 意图路由

> 分组：记忆与知识

**意图路由：Agent 的大脑与决策中枢** —— 不是所有问题都需要同一个处理流程。先识别意图，再分流 —— 从单车道升级为立交桥。

## 核心概念 {#concept}

### 路由器的三重身份

- **意图分类器**：识别「用户到底想要什么」；
- **任务规划器**：复杂请求拆成子任务序列（本场景简化为单跳）；
- **Agent Loop 的节拍器**：每轮循环先经过路由，决定继续、转向还是终止。

### LLM 路由 vs 规则路由

|   | 关键词规则 | LLM 分类 | 混合（生产推荐） |
| --- | --- | --- | --- |
| 延迟 | 0 | +1 次模型调用 | 规则快筛 + LLM 兜底 |
| 准确率 | 表面词匹配，易误判 | 语义级理解 | 双保险 |
| 成本 | 免费 | 按 Token 计费 | 可控 |

::: tip 💡 面试考点
「多 Agent 系统怎么分工？」—— 第一步就是路由：一个入口 Agent 识别意图后，把任务分给领域 Agent（天气 Agent / 客服 Agent / 代码 Agent）。**路由是多智能体协作的前置能力**，Step12 会用到。
:::

## 实现要点 {#impl}

### 为什么需要路由

把天气、计算、闲聊全塞进一个 ReAct 循环，会遇到三个问题：延迟高（每步都要过模型）、成本贵（Token 浪费）、不可控（闲聊也可能触发工具）。解法：**入口先做一次轻量分类，再按意图走专属通道**。

```java
// 1. 意图识别：让模型输出分类 JSON
String ROUTER_PROMPT = """
        你是意图识别路由器。只输出 JSON：
        {"intent": "weather|math|chat", "reason": "一句话理由"}
        """;
String intent = Json.str(model.chat(...), "intent");

// 2. 决策中枢：按意图路由
return switch (intent) {
    case "weather" -> handleWeather(input);   // 走工具
    case "math"    -> handleMath(input);      // 走计算器
    default        -> handleChat(input);      // 走对话
};
```

## ToyAgent vs ToyAgentScope {#compare}

**意图路由：结构化输出当决策中枢** —— 不再要求模型「只输出 JSON」，而是给框架一个 Java record，直接拿回强类型对象。

```java
record Intent(String intent, String reason) {}   // 框架据此生成 JSON Schema

Msg m = classifier.call(List.of(new UserMessage(input)), Intent.class, ctx).block();
Intent intent = Texts.structured(m, Intent.class);   // 安全读取，拿不到返回 null

switch (intent.intent()) {
    case "weather" -> CommonTools.weather(city);      // 路由到工具
    case "math"    -> Calc.eval(expr);                // 路由到计算器
    default        -> chatter.call(...);              // 路由到闲聊智能体
}
```

### 结构化输出的两条实现路径

| 路径 | 原理 | 适用 |
| --- | --- | --- |
| 原生（nativeStructuredOutput=true） | 请求带 response_format: json_schema | OpenAI 官方等完整支持 json_schema 的服务 |
| 工具（false） | 临时注入 generate_response 工具，模型以调用该工具的方式交答案 | 大多数 OpenAI 兼容服务 |

::: warning ⚠️ 实测踩坑：火山方舟上原生模式不稳定
在 Ark 的 deepseek-v4.1-flash 上，原生路径多次出现字段丢失、JSON 混入解释文字甚至报 “No structured output”。改为 `OpenAIChatModel.builder().nativeStructuredOutput(false)` 走工具路径后 5/5 稳定 —— 本项目在 `Models.build()` 里统一这样配置。
:::

## 对应教程 {#chapters}

- ch06b 意图识别与决策中枢
- ch07 Agent Loop
