---
step: '12'
---

# 12 多智能体

> 分组：协作与派遣

**多智能体：从一个大脑到一支团队** —— 规划者拆任务，研究员找素材，写手出内容，审查员把关 —— 结构化消息在角色间流动。

## 核心概念 {#concept}

### 什么时候该拆出多个智能体

- **上下文装不下**：一个 Agent 要装所有领域的提示词与工具；
- **职责混杂**：既当研究员又当审查员，自己查自己容易"放水"；
- **并行需求**：多个子任务可以同时执行。

### 三种经典协作模式

| 模式 | 结构 | 适用 |
| --- | --- | --- |
| 流水线（本场景） | 顺序传递，单向 | 内容生产、报告生成 |
| 主管-工人 | 主管动态分派汇总 | 任务边界不确定的调研 |
| 辩论/评审 | 多 Agent 互审博弈 | 高可靠性要求的结论 |

### 别忘了 A2A 与成本

跨系统协作需要 Agent 间协议（A2A：能力卡片 + 任务协商）；而每加一个角色，延迟与 Token 成本线性上涨 —— **能用单 Agent + 好提示词解决的，不要上多 Agent**。

## 实现要点 {#impl}

### 一条流水线

🧭 规划者 → 🔬 研究员 → ✍️ 写手 → 🔍 审查员 → 🏁 汇总交付

```java
// 每个角色 = 同一个 ChatModel + 不同的系统提示词
String plan = model.chat(List.of(Message.system("""
        你是规划者（Planner）。把任务拆解为三个角色的执行计划：
        {"plan": [{"role": "研究员", "task": "..."},
                  {"role": "写手", "task": "..."},
                  {"role": "审查员", "task": "..."}]}
        """), Message.user(input)));

// 上游产出作为下游输入 —— 消息在角色间流动
String notes = model.chat(List.of(
        Message.system("你是研究员，输出 3 条以内研究要点。"),
        Message.user(input)));
String draft = model.chat(List.of(
        Message.system("你是写手。基于研究笔记写短文。\n笔记: " + notes),
        Message.user(input)));
// 审查员可以打回 → 触发修改循环
```

::: tip 💡 协作的本质
多智能体协作没有魔法：**每个角色都是同一个模型换了份系统提示词**，区别只在消息流的结构。设计多 Agent 系统 = 设计「谁在什么时刻看见什么信息」。
:::

## ToyAgent vs ToyAgentScope {#compare}

**多智能体：普通 Java 编排 4 个角色** —— 规划者 → 研究员 → 写作者 → 审查者，各是一个独立 ReActAgent；规划和审查用结构化输出返回强类型对象。

Planner → Plan → Researcher → 笔记 → Writer → 初稿 → Reviewer → Review → 不通过则重写

```java
record Plan(List<String> steps) {}
record Review(boolean pass, String comment) {}

Plan plan = Texts.structured(planner.call(msgs, Plan.class, ctx()).block(), Plan.class);
String notes = text(researcher, "任务：" + input + "
计划：" + plan);
String draft = text(writer, "研究笔记：
" + notes);
Review review = Texts.structured(reviewer.call(..., Review.class, ctx()).block(), Review.class);
if (!review.pass()) draft = text(writer, "审查意见：" + review.comment() + "，请重写");
```

::: tip 💡 2.0 移除了 Pipeline / MsgHub
AgentScope 1.x 的 `SequentialPipeline`、`FanoutPipeline`、`MsgHub` 在 2.0 中已删除。官方迁移指南的建议是：用**普通代码编排 + 子代理（Step13）+ 事件流**组合多智能体；并行可用 `Mono.zip`。
:::

## 对应教程 {#chapters}

- ch10 多 Agent 系统
- ch14 多 Agent 系统架构
