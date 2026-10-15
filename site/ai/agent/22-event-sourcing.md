---
step: '22'
---

# 22 事件溯源

> 分组：人机协同与安全

**事件溯源：会话即日志，日志即状态** —— 对话的每一刻都追加为不可变事件（JSONL），Agent 的记忆从日志回放投影而来 —— 服务重启不丢，审计天然免费。

## 核心概念 {#concept}

### 这个架构白送的能力

- **持久化**：重启后状态从日志完整恢复 —— 聊两轮 → 重启服务 → 还记得你名字；
- **审计**：谁在什么时候说了什么，日志一条不缺（合规刚需）；
- **时间旅行 / fork**：截取日志前 N 条 = 任意历史时点的会话快照 —— 生产级框架的子代理 fork 正是「复制事件前缀」；
- **调试**：出问题不用猜「模型当时看到了什么」，回放即可精确复原。

::: info 🔍 动手观察
聊两轮后打开 `events/step22-events.jsonl` 看原始事件流；点「重置」会删掉日志 —— 记忆归零，因为状态只存在于日志里。**删日志 = 删状态**，这正是事件溯源的题眼。
:::

生产对照：生产级框架有 16 种 sealed 领域事件（含 TOOL_CALLED、审批、压缩、子代理等）+ 校验和防篡改；教学版 4 种足够看清骨架。

## 实现要点 {#impl}

### 换个方式存状态

之前所有场景的记忆都是内存里的 `List<Message>` —— 服务一重启就没了。生产级框架换了个思路（也是 Event Sourcing / Kafka / Git 的共同思想）：**不存「当前状态」，只存「发生过什么」**。

```jsonl
// events/step22-events.jsonl —— append-only，永不改写
{"seq":1,"ts":1728380000000,"type":"SESSION_STARTED","payload":{"scenario":"step22"}}
{"seq":2,"type":"USER_MESSAGE_APPENDED","payload":{"content":"我叫王加伦"}}
{"seq":3,"type":"AGENT_REPLY_APPENDED","payload":{"content":"你好，王加伦！…"}}
{"seq":4,"type":"TURN_COMPLETED","payload":{"events":3}}
{"seq":5,"type":"USER_MESSAGE_APPENDED","payload":{"content":"我叫什么名字？"}}
…
```

### 三件套

| 环节 | 做什么 | 关键点 |
| --- | --- | --- |
| 事件追加 | 每轮对话追加 4 类事件：SESSION / USER / AGENT_REPLY / TURN_COMPLETED | append-only，seq 单调递增，一行一条 JSON |
| 回放投影 | 每轮开始前重放全部事件，重建会话上下文 | 记忆 = 投影视图，不是内存变量 |
| 类型过滤 | 投影只取 USER/AGENT 两类进上下文 | 其余事件留在日志里服务审计 —— 视图与真相分离 |

```java
private List<Message> project(List<Map<String, Object>> log) {
    for (Map<String, Object> e : log) {
        switch (String.valueOf(e.get("type"))) {
            case EV_USER   -> history.add(Message.user(content));
            case EV_AGENT  -> history.add(Message.assistant(content));
            default        -> { /* 不进上下文，但留在日志里 */ }
        }
    }
}
```

## ToyAgent vs ToyAgentScope {#compare}

**事件溯源：状态 = 回放事件的投影** —— 每轮都用全新 sessionId（框架内存里什么都没有），先回放 JSONL 投影出历史，再连同新问题交给智能体；执行过程中的事件追加写回日志。

回放 JSONL → 投影成 List&lt;Msg&gt; → 全新 session 调用 → listen 事件流 → 追加 USER / TOOL / REPLY / TURN 事件

```java
List<Msg> history = EventStore.replay();          // USER_MESSAGE_APPENDED → UserMessage …
EventStore.append("USER_MESSAGE_APPENDED", Map.of("text", input));
Tracer.Result r = Tracer.of(log)
        .listen(e -> { if (e.getType() == AgentEventType.TOOL_CALL_END)
                           EventStore.append("TOOL_CALLED", ...); })
        .run(agent.streamEvents(concat(history, new UserMessage(input)), freshCtx));
EventStore.append("AGENT_REPLY_APPENDED", Map.of("text", r.text()));
EventStore.append("TURN_COMPLETED", Map.of("reason", r.reason()));
```

::: tip 💡 版本说明
AgentScope 2.1 起内置 session event log（AgentSession、回放、steer/resume）；本项目锁定的 2.0.4 还没有，所以用 2.0 的事件流自己实现 —— 原理完全相同，升级后可直接对照官方实现。
:::

## 对应教程 {#chapters}

- 生产级框架 · Session Event Log / 回放投影
