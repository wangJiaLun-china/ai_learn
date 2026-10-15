---
step: '02'
---

# 02 提示词

> 分组：基础内核

**提示词工程：给模型一份角色说明书** —— 同一个模型，换一份系统提示词就换了一种人格 —— 这是成本最低的「能力开发」。

## 核心概念 {#concept}

### 三层结构：角色 / 背景 / 任务

| 层 | 回答的问题 | 示例 |
| --- | --- | --- |
| 角色 | 你是谁 | 王加伦的技术助理 |
| 背景 | 在什么语境下工作 | 读者是 Java 工程师、AI 新手 |
| 任务与约束 | 怎么干、什么不能干 | 先结论后展开、可拒答 |

::: warning ⚠️ 常见误区
把提示词写成一句「你是个 AI 助手」。缺少角色锚点与约束条件时，模型的输出风格完全随机 —— 提示词工程的本质是**用自然语言写一份可执行的规格说明**。
:::

### C.L.E.A.R 原则

- **C**oncise 简洁 —— 删掉一切不影响行为的词；
- **L**ogical 逻辑 —— 约束之间不能互相矛盾；
- **E**xplicit 明确 —— 「简短」不如「50 字以内」；
- **A**daptive 适配 —— 提供示例（Few-shot）校准格式；
- **R**obust 鲁棒 —— 声明边界行为（不知道就拒答）。

在 ToyAgent 网页里试试「用一句话解释什么是 Agent」，观察模型如何严格遵守**约束条款** —— 这就是提示词的「执行力」。

## 实现要点 {#impl}

### 与 Step01 唯一的区别

调用模型前，先塞入一条 `system` 消息。仅此而已，但输出质量天差地别。

```java
private static final String SYSTEM_PROMPT = """
        # 角色
        你是王加伦的技术助理，专注讲清楚 AI Agent 的原理与工程实践。

        # 背景
        读者是正在学习智能体开发的 Java 工程师。

        # 任务与约束
        1. 回答使用简体中文，先给结论再展开。
        2. 若用户要求"用一句话"，必须严格只回答一句话。
        3. 不确定的内容如实说明，禁止编造。
        """;

@Override
public String chat(String input) {
    return model.chat(List.of(
            Message.system(SYSTEM_PROMPT),  // 唯一新增
            Message.user(input)));
}
```

## ToyAgent vs ToyAgentScope {#compare}

**提示词工程：sysPrompt 三层结构** —— 系统提示词从「手动塞进消息列表第一条」变成 builder 上的一个属性，还能被中间件动态改写。

### 三层结构

【角色】你是谁 → 【背景】在什么场景 → 【任务与约束】做什么、怎么输出

```java
ReActAgent.builder()
        .name("prompt")
        .sysPrompt("""
            【角色】你是「ToyAgentScope」教学助手，擅长用通俗类比解释 AI 智能体概念。
            【背景】用户正在学习 AgentScope Java 2.0，基础薄弱，喜欢直白的例子。
            【任务与约束】先一句话结论，再一个生活类比；不超过 120 字；不确定就说不知道。
            """)
        .model(model)
        .build();
```

::: info 🚀 进阶：动态提示词
固定提示词用 `sysPrompt`；需要每轮注入动态信息（用户画像、时间、权限）时，实现 `MiddlewareBase.onSystemPrompt(agent, ctx, current)` 返回改写后的提示词 —— Step18 用它注入用户画像。
:::

## 对应教程 {#chapters}

- ch04 提示词工程
