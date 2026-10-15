---
step: '05'
---

# 05 记忆

> 分组：记忆与知识

**记忆系统：每一次请求，都带上该带的历史** —— 模型本身没有记忆。所谓记忆，是把历史重新放进上下文窗口 —— 加上超限后的压缩策略。

## 核心概念 {#concept}

### 上下文窗口是稀缺资源

模型一次能处理的 Token 数有限（128K 起步但昂贵且存在「中间遗忘」）。历史无限增长时需要两板斧：

| 策略 | 做法 | 代价 |
| --- | --- | --- |
| 滑动窗口 | 只保留最近 N 轮 | 丢失早期信息 |
| 摘要压缩 | 旧对话摘成一条 system 消息 | 摘要本身有损 |
| 混合（本场景） | 滑窗 + 摘要并用 | 工程复杂度略高 |

### 短期 vs 长期记忆

- **短期记忆**：就是本场景的对话历史，生命周期 = 一次会话；
- **长期记忆**：跨会话持久化（向量库 / 数据库），使用时检索注入 —— 技术上就是 RAG（见 Step07）；
- **工作记忆**：ReAct 循环中当前任务的中间状态（Thought / Observation）。

::: warning ⚠️ 上下文工程
「往上下文里放什么、放多少、什么时候清掉」正在成为一门显学 —— Context Engineering。提示词工程决定模型**怎么答**，上下文工程决定模型**看见什么**。看见什么，决定它知道什么。
:::

::: info 🚀 动手观察
先发「我叫王加伦」，再发「我叫什么名字？」—— 模型答对完全依赖 ToyAgent 网页时间线里的「记忆窗口」计数。点【重置】后再问一次，它就真的忘了。
:::

## 实现要点 {#impl}

### 记忆的真相

先破除一个误解：模型没有数据库、没有存储。**短期记忆 = 把历史消息一并发给模型**。之前说过「我叫王加伦」它能记住，只是因为那轮对话还在这次的上下文里。

```java
public String chat(String input) {
    history.add(Message.user(input));

    // 历史超限 → 压缩：最旧两轮摘成一句话
    if (history.size() > MAX_ROUNDS * 2) {
        compress();
    }

    List<Message> context = new ArrayList<>();
    context.add(Message.system(SYSTEM_PROMPT));
    if (!summary.isEmpty()) {
        context.add(Message.system("【历史摘要】" + summary));
    }
    context.addAll(history);          // ← 记忆的全部秘密

    String answer = model.chat(context);
    history.add(Message.assistant(answer));
    return answer;
}
```

## ToyAgent vs ToyAgentScope {#compare}

**记忆系统：会话状态 + 自动压缩** —— 短期记忆交给 AgentState（按 sessionId 隔离），上下文压缩交给 HarnessAgent 的 compaction。

### 两种记忆，两个组件

|   | ToyAgent | ToyAgentScope |
| --- | --- | --- |
| 多轮记忆 | 自己维护 history 列表 | 同一 sessionId 连续调用，框架自动带上下文 |
| 压缩 | 超过 4 轮把最早两条拼成摘要字符串 | `CompactionConfig`：达到阈值调用模型总结前缀，只保留最近 N 条原文 |
| 清空 | history.clear() | `agent.clearContext(ctx)` |

```java
HarnessAgent.builder()
        .name("memory").agentId("step05")
        .model(model)
        .workspace(Path.of("data", "workspace", "step05"))
        .compaction(CompactionConfig.builder()
                .triggerMessages(8)          // 8 条消息（约 4 轮）触发
                .keepMessages(4)             // 保留最近 4 条原文
                .summaryPrompt(SUMMARY_PROMPT)   // {messages} 占位符
                .build())
        // 本场景只演示记忆，关掉 harness 的其他内置能力
        .disableFilesystemTools().disableShellTool().disableWebTools()
        .disableMemoryTools().disableMemoryHooks().disableSubagents()
        .build();
```

压缩后，上下文开头会出现一条名为 `__compaction_summary__` 的摘要消息（`ConversationCompactor.SUMMARY_MSG_NAME`），Tracer 据此在轨迹里标出 `compress` 节点。

::: warning ⚠️ 实测：默认摘要是英文
CompactionConfig 的默认摘要提示词是英文的 Context Extraction 模板，中文对话也会得到英文摘要。用 `.summaryPrompt(...)` 换成中文，并要求「必须保留姓名、喜好等个人信息」。
:::

::: info 🧪 试一试
连续说 4 句个人信息（名字、颜色、城市、宠物），第 5 句问「我叫什么？我的猫叫什么？」—— 轨迹里出现 compress，答案依然正确。
:::

## 对应教程 {#chapters}

- ch04b 上下文工程
- ch06 Agent 的记忆系统
