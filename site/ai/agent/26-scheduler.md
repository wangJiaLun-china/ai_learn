---
step: '26'
---

# 26 定时工具

> 分组：扩展与形态

**定时工具：给智能体装上时间维度** —— 之前的工具都是即时的：调用 → 执行 → 返回。定时工具把「什么时候做」也变成决策对象 —— 任务在后台执行，结果落盘可回看。

## 核心概念 {#concept}

### 从即时工具到时间维度

「每天早上 8 点给我汇总新闻」「30 秒后提醒我开会」—— 这类需求的共同点：**请求已返回，行动在未来**。智能体需要一种工具，能把「何时做」注册进调度器，然后放心地把控制权交还。

::: tip 💡 关键工程点
HTTP 响应已经返回，任务却在未来执行 —— 所以触发结果**不能塞进本次响应**，要落盘（事件溯源，呼应 Step22），并在后续对话中汇报。
:::

### 三种调度能力

| 能力 | 调度原语 | 本场景指令 |
| --- | --- | --- |
| 一次性 | schedule + delay | 「定时：5秒 提醒我喝水」→ job-1 |
| 循环 | scheduleAtFixedRate（首延迟 = 周期） | 「循环：每10秒 报一次时」→ 已触发次数持续增长 |
| 管理 | 列表 / 取消（job_id 定位） | 「任务列表」「取消：job-2」「完成了什么」 |

### 试试这样玩（感受异步）

- 「定时：5秒 提醒我喝水」→ 立刻「任务列表」看到 pending；等 5 秒再问变 done/触发；
- 「循环：每10秒 报一次时」→ 隔一会儿「任务列表」，触发次数在涨；
- 「完成了什么」—— 从 events/step26-tasks.jsonl 读回已触发的全部记录。

生产化路径：任务持久化到数据库（重启不丢）、到点推送改 SSE/WebSocket、循环任务加最大次数与退避、与 Step20 审批门禁联动（高危定时任务需审批）。

## 实现要点 {#impl}

### 本场景的骨架

注册任务拿 job_id → 响应立即返回 → 调度线程到点触发 → 结果 append 到事件日志

```java
// 守护线程调度池（生产用持久化调度器/延时队列）
ScheduledExecutorService pool = Executors.newScheduledThreadPool(1, r -> {
    Thread t = new Thread(r, "step26-scheduler");
    t.setDaemon(true);
    return t;
});

// 一次性：n 秒后触发
pool.schedule(() -> fire(id, content), delaySeconds, TimeUnit.SECONDS);

// 触发即落盘：append-only JSONL（事件溯源）
Files.writeString(eventFile,
    "{"time":"" + time + "","job":"" + id + ""}
",
    CREATE, APPEND);
```

## ToyAgent vs ToyAgentScope {#compare}

**定时工具：让模型自己排任务** —— schedule_once / schedule_repeat / list_jobs / cancel_job / fired_results 五个工具，由模型理解自然语言并调用；触发时再调一个提醒智能体生成文案，写入 JSONL。

```java
@Tool(name = "schedule_once", description = "创建一次性定时任务：seconds 秒后执行 task")
public String once(@ToolParam(name = "seconds") Integer seconds, @ToolParam(name = "task") String task) {
    Job job = new Job("job-" + seq.incrementAndGet(), task, "一次性");
    job.future = scheduler.schedule(() -> fire(job, true), seconds, TimeUnit.SECONDS);
    return "已创建 " + job;
}

private void fire(Job job, boolean once) {
    int n = job.fired.incrementAndGet();                         // 手写版这里忘了自增
    String text = reminder.call(...).block().getTextContent();   // 触发时再调一次智能体
    Files.writeString(TASK_LOG, Json.write(event) + "
", CREATE, APPEND);   // Jackson：自动转义
    if (!once && n >= MAX_REPEAT) job.future.cancel(false);      // 循环任务上限，防止无限烧 Token
}
```

::: warning ⚠️ 真实模型下的成本意识
每次触发都会调一次模型。循环任务一定要设上限（本项目 5 次）和最小间隔（5 秒），否则忘了取消的任务会一直消耗 Token。
:::

::: tip 💡 框架的调度扩展
AgentScope 提供 `agentscope-extensions-scheduler-quartz / xxl-job`，可以按 Cron 调度整个智能体；2.0.4 只内置了 DashScope 模型配置，所以教学里用 ScheduledExecutorService 演示同样的原理。
:::

## 对应教程 {#chapters}

- 生产级框架 · domain/tool/schedule
