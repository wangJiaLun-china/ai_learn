---
step: '13'
---

# 13 子代理

> 分组：协作与派遣

**子代理：spawn 与 fork 的派遣艺术** —— 主代理不必亲自做所有事 —— 把子任务派给带独立上下文的子代理。spawn 全新出发，fork 继承当前对话。

## 核心概念 {#concept}

### 为什么需要子代理

一个代理的上下文窗口装不下所有领域知识，也装不下所有任务。子代理 = **带独立上下文的分身**：主代理只派任务、收结论，中间过程不占主会话。

::: tip 💡 和 Step12 的区别
Step12 是**固定流水线**（规划→研究→写作→审查），角色编排写死在代码里；本步是**主代理动态派遣** —— 模型自己决定派谁、怎么派，即 Claude Code 的 Task 工具。
:::

### spawn 与 fork

| 维度 | spawn（SpawnInProcessProvider） | fork（ForkInProcessProvider） |
| --- | --- | --- |
| 上下文 | 全新：只有职责提示词 | 复制父会话再出发 |
| 适合 | 无状态专项任务（调研、翻译） | 「接着当前话题继续做」 |
| 记忆 | 什么都不知道 | 知道对话里出现过的一切 |

### 试试这样玩（体会上下文差异）

- 先说「记住：我最喜欢紫色」，再「fork 一个子代理，让它写一句贺词」—— 贺词里带紫色；
- 「再 spawn 一个子代理写贺词」—— 全新实例，贺词是通用的；
- 「对比两次贺词」—— 差异即 spawn 与 fork 的差异。

生产化路径：子代理挂独立工具集与模型档位、结果做结构化校验、派遣深度限制（防子代理再生子代理失控）、生产级框架 的 SubagentRegistry 还支持子代理注册表化复用。

## 实现要点 {#impl}

### 本场景的骨架

主代理决策 → dispatch_subagent → 子代理独立循环 → 结果回填主会话

```java
// fork：子代理的 system 里带上父会话
StringBuilder sys = new StringBuilder("【子代理身份】").append(spec.specialty());
if (fork) sys.append("
【继承的父会话上下文】
")
             .append(String.join("
", parentTranscript));
else       sys.append("
（spawn 模式：全新实例）");

childMessages.add(Message.system(sys.toString()));
childMessages.add(Message.user(task));
String report = model.chat(childMessages);   // 子代理独立一轮
```

## ToyAgent vs ToyAgentScope {#compare}

**子代理：spawn 用框架，fork 自己补** —— spawn = 把 ReActAgent 注册成工具（全新上下文）；fork = 读出父会话注入子代理（继承记忆）。

```java
// spawn：智能体即工具（SubAgentTool 的参数是 session_id + message）
toolkit.registration()
        .subAgent(() -> child("writer", "你是写作者子代理…"),
                  SubAgentConfig.builder().toolName("spawn_writer")
                          .description("spawn 一个写作者子代理（看不到当前对话）")
                          .forwardEvents(false).build())
        .apply();

// fork：普通 @Tool，读出父会话作为子代理的背景
@Tool(name = "fork_subagent", description = "fork 一个继承当前对话历史的子代理")
public String fork(@ToolParam(name = "task") String task) {
    String transcript = parent.getAgentState(ctx).getContext().stream()...;
    return child("forked", "你继承了父代理的对话：
" + transcript).call(task)...;
}
```

|   | spawn | fork |
| --- | --- | --- |
| 上下文 | 只有主代理写给它的 message | 父会话全文 + 任务 |
| 成本 | 低 | 高（带历史） |
| 适合 | 独立子任务、并行调研 | 需要延续用户偏好/约定的任务 |

::: info 🧪 对比实验
先「记住：我最喜欢紫色」，再「fork 一个子代理写贺词」和「再 spawn 一个子代理写贺词」—— 只有 fork 出来的那句贺词带紫色。
:::

::: tip 💡 forwardEvents
设为 true 时子代理的事件会转发进父代理的事件流（适合做嵌套时间线）；本项目设为 false，避免子代理的 AGENT_RESULT 混入父代理结果。HarnessAgent 另有 `agent_spawn` / `agent_send` 等更完整的子代理工具集。
:::

## 对应教程 {#chapters}

- 生产级框架 · SubagentRegistry / SpawnInProcessProvider / ForkInProcessProvider
