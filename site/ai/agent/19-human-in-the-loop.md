---
step: '19'
---

# 19 人工介入

> 分组：人机协同与安全

**人工介入：ask_user_question，把人变成可等待的工具** —— 信息缺失时别瞎猜 —— 主动问人。参考 生产级框架的 CompletableFuture 阻塞-唤醒，教学版用会话挂起实现同一语义。

## 核心概念 {#concept}

### 为什么需要问人

「帮我订机票」—— 没说出发地。猜一个？错了浪费时间，对了纯属运气。**聪明的智能体要懂得什么时候该开口问**，这正是 生产级框架 ask_user_question 工具的职责。

::: tip 💡 核心思想：把「异步的人」翻译成「同步的工具结果」
人不在进程里，没法像函数一样被调用。生产级框架的解法：**CompletableFuture 阻塞-唤醒** —— 工具执行线程阻塞在 Future 上，问题推给前端，人的回复一到就 complete，线程醒来拿到「工具结果」继续跑。
:::

### 试试这样玩

- 发「帮我订一张去北京的机票」—— 轨迹出现 `ask_user_question` 调用 + 会话挂起 `PENDING_ASK`；
- 回复「从上海出发」—— 轨迹出现「人工答复回填」，任务带着完整信息继续；
- 注意：你的答复**没有经过模型决策**，它直接作为事实注入 —— 人的回答是权威输入。

延伸：生产级框架 里问人不只一种 —— 主动提问（ask）、方案选择（ask_user_question）、打断纠偏（steer）构成完整的「人在回路」谱系。

## 实现要点 {#impl}

### 教学版：会话挂起 + 续答

ToyAgent 是同步 HTTP 架构，没有常驻线程可阻塞。同一思想的另一种实现：**把等待摊开成两次请求**。

```java
// 第一次请求：模型发现缺出发地 → 发起 ask_user_question
if (tool.equals("ask_user_question")) {
    pendingQuestion = question;      // 问题持久化在会话状态（挂起）
    return question + "（直接回复即可）";  // turn 暂停，先答复人
}

// 第二次请求：用户的回复不进模型，而是作为人工答复回填
if (pendingQuestion != null) {
    history.add(Message.user("人工答复：" + input));
    return complete(pendingQuestion); // turn 恢复，带着完整信息继续
}
```

|   | 生产级框架 生产版 | ToyAgent 教学版 |
| --- | --- | --- |
| 等待机制 | CompletableFuture 阻塞执行线程 | pendingQuestion 会话状态挂起 |
| 答复到达 | future.complete() 唤醒 | 下一条消息回填挂起位 |
| 语义 | 完全一致：人的一次回答 = 一次工具结果注入 |   |

## ToyAgent vs ToyAgentScope {#compare}

**人工介入：把人变成一个外部执行工具** —— ask_user 是 externalTool(true) 的 ToolBase：框架不在本地执行它，而是挂起并把问题交出去；人类答复后用同一个 toolCallId 回填，从断点续跑。

模型调用 ask_user → REQUIRE_EXTERNAL_EXECUTION → TOOL_SUSPENDED 挂起 → 用户答复 → ToolResultMessage(同一 id) → 继续推理

```java
static class AskUserTool extends ToolBase {
    AskUserTool() {
        super(ToolBase.builder()
                .name("ask_user")
                .description("向用户提一个澄清问题并等待人工答复")
                .inputSchema(Map.of("type", "object",
                        "properties", Map.of("question", Map.of("type", "string")),
                        "required", List.of("question")))
                .externalTool(true));          // 关键：外部执行
    }
}

// 挂起：记住待回填的调用
if (r.is(GenerateReason.TOOL_SUSPENDED)) pending = lastToolUse(r.msg(), "ask_user");

// 续跑：答复包装成同一个 toolCallId 的工具结果
agent.streamEvents(List.of(new ToolResultMessage(pending.getId(), pending.getName(), "人工答复：" + input)), ctx);
```

::: warning ⚠️ 实测：提示词要“堵死”文字提问
第一版提示词只说「缺信息时调用 ask_user」，模型却直接在回复正文里提问（没走工具，也就不会挂起）。改成「不要在回复正文里直接提问 —— 必须调用 ask_user 工具」后稳定走工具。
:::

## 对应教程 {#chapters}

- 生产级框架 · ask / ask_user_question
