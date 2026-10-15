---
step: '17'
---

# 17 ReAct 运行时

> 分组：运行时与工程化

**ReAct 运行时：turn/step 两级循环 + 上下文裁剪** —— 参考 生产级 Agent 框架 的 ReactLoopAgent（994 行）拆出的教学骨架：循环只能以 TurnEndReason 收场。

## 核心概念 {#concept}

### 从"循环"到"运行时"

Step03 的 ReAct 循环只有一个 while。真实的运行时（生产级框架的 ReactLoopAgent，994 行）多了三样东西：**两级循环、上下文预算、统一的结束原因**。本场景把这三样拆出来讲。

### 机制三：步数保险丝

单回合最多 6 步（生产级框架里是 50）。发「死循环测试」—— Mock 模型会永远发起 echo 工具调用，亲眼看到 MAX_STEPS 保险丝起跳，回合以 `TurnEndReason.MaxSteps` 收场。

### 和场景 15 的分工

|   | 11 · Loop 运行时 | 16 · ReAct 运行时 |
| --- | --- | --- |
| 关注点 | 输入/输出守卫、异常兜底 | 循环结构本身：两级驱动 + 预算 + 结束原因 |
| 回答的问题 | "跑飞了怎么办" | "循环怎么停、上下文怎么管" |
| 对应生产级框架 | Guard / Harness 层 | ReactLoopAgent（994 行）教学骨架 |

消化完这两个场景，再去看 生产级框架 源码：ToolDefinition（70 行）→ ToolRegistry → ReactLoopAgent → ToolCallExecutor（763 行并行调度），就是从玩具到工业级的完整阶梯。

## 实现要点 {#impl}

### 机制一：turn/step 两级循环

chat() = 1 个 turn → while = step 循环 → 模型 / 工具续步 → TurnEndReason 收场

与 Step03 的关键区别：工具执行完**不结束回合，而是续步**（mid-turn continuation）—— 观察结果进历史，循环继续，直到出现结束原因。

```java
private TurnEndReason runTurn(String input) {
    for (long step = 1; step <= MAX_STEPS_PER_TURN; step++) {
        String raw = model.chat(buildRequest());   // 系统提示词 + 裁剪后的历史
        // 模型给出 final → Completed；发起工具 → 执行后续步
        if (hasFinal(raw)) return new TurnEndReason.Completed("模型给出最终答案");
        String obs = executeTool(raw);
        history.add(Message.user("Observation: " + obs));  // 观察回填 → 续步
    }
    return new TurnEndReason.MaxSteps(MAX_STEPS_PER_TURN); // 保险丝
}
```

::: tip 💡 TurnEndReason：sealed 封闭类型
`sealed interface TurnEndReason permits Completed, MaxSteps, Error` —— 所有"循环为什么停"被收敛成一个封闭代数类型。sealed 保证编译器穷尽检查：新增结束原因时，所有处理它的地方都会被编译器提醒补分支。
:::

### 机制二：上下文裁剪 truncateToBudget

```java
// token 估算：教学化近似 token ≈ 字符数 / 4（生产级框架 同款）
int tokens = history.stream().mapToInt(m -> m.content().length() / 4).sum();
// 超预算：从最老的消息开始丢，保底留最近 4 条
while (tokens > BUDGET && history.size() > KEEP_MIN) history.remove(0);
```

历史跨对话累积，多聊几轮后轨迹里会出现「上下文裁剪」事件 —— 这就是生产级框架在每次调模型前做的事（它还叠了 LLM 摘要压缩，见场景 05 与 18）。

## ToyAgent vs ToyAgentScope {#compare}

**ReAct 运行时：Middleware 裁剪上下文** —— turn 跨回合保留历史，step 由 maxIters 限制；ContextTrimMiddleware 在每次调模型前按 token 预算裁剪「发给模型的视图」。

### Middleware 的 5 个拦截点（2.0.4）

| 方法 | 时机 | 本项目用法 |
| --- | --- | --- |
| onAgent | 整个 call 前后 | — |
| onReasoning | 每次推理 | — |
| onModelCall | 每次调模型 | Step17 上下文裁剪 |
| onActing | 每批工具调用 | （可改写参数） |
| onSystemPrompt | 生成系统提示词 | Step18 注入画像 |

```java
class ContextTrimMiddleware implements MiddlewareBase {
    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        if (estimate(input.messages()) <= BUDGET_TOKENS) return next.apply(input);
        List<Msg> trimmed = dropOldestUntilWithinBudget(input.messages());   // 保留 system，从用户消息开头
        ctx.get(TraceLog.class).add("compress", "上下文裁剪", ...);
        return next.apply(new ModelCallInput(trimmed, input.tools(), input.options(), input.model()));
    }
}
```

::: warning ⚠️ 裁剪的两个细节
① 只改本次发给模型的视图，会话状态里的完整历史不变；② 裁剪后第一条非 system 消息必须是真正的用户消息，不能是孤立的工具结果，否则 OpenAI 兼容接口会报错。另外注意 Middleware 签名里的 `Agent` 是 `io.agentscope.core.agent.Agent`，别和本项目的 Agent 接口混了。
:::

::: info 🧪 死循环测试
check_job 永远返回 RUNNING，模型会不断轮询，第 6 步触发 EXCEED_MAX_ITERS，TurnEndReason = MaxSteps。
:::

## 对应教程 {#chapters}

- 生产级框架 · ReactLoopAgent / TurnEndReason
