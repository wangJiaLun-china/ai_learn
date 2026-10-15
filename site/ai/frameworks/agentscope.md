# AgentScope Java

> 板块：框架生态 · 本仓库 [ToyAgentScope](https://github.com/wangJiaLun-china/ai_learn/tree/main/ToyAgentScope) 用 AgentScope Java **2.0.4** 重做了全部 26 个场景，每篇场景笔记的「ToyAgent vs ToyAgentScope」小节有逐场景对照。

## 核心思路 {#overview}

同一个 `Agent.chat()` 契约，内部从「全部手写」换成「装配框架组件」。每个场景的实现只做三件事：

1. **装配**框架组件（ReActAgent / Toolkit / Middleware / 权限…）；
2. **调用** `agent.streamEvents(...)`；
3. 用 `Tracer` 把**类型化事件流**翻译成执行轨迹。

::: tip 💡 智能体无状态
AgentScope 2.0 删除了 1.x 的 Memory 对象。智能体实例本身不存对话，会话状态按 `(userId, sessionId)` 存放，同一个实例可同时服务很多会话。
:::

## 手写零件 → 框架组件 {#mapping}

| 手写版 ToyAgent | AgentScope 2.0 组件 | 场景 |
| --- | --- | --- |
| ChatModel + OpenAiChatModel | `Model` / `OpenAIChatModel` | [01](../agent/01-chat) |
| JSON 协议 + 手写 ReAct 循环 | `ReActAgent` + Function Calling + `maxIters` | [03](../agent/03-react) |
| 工具描述字符串 + switch 分发 | `@Tool` / `@ToolParam` + `Toolkit` | [04](../agent/04-tool-call) / [11](../agent/11-tool-registry) |
| history 列表 + 摘要字符串 | AgentState 会话上下文 + HarnessAgent `compaction` | [05](../agent/05-memory) / [18](../agent/18-full-pipeline) |
| 「只输出 JSON」提示词 + 手动解析 | 结构化输出 `call(msgs, Record.class)` | [06](../agent/06-intent-router) / [12](../agent/12-multi-agent) / [16](../agent/16-workflow) |
| 进程内模拟 tools/list | 真 MCP（官方 SDK，stdio 子进程） | [09](../agent/09-mcp) |
| 技能选择器 + switch | `SKILL.md` + `ClasspathSkillRepository` | [10](../agent/10-skills) |
| 手写 dispatch_subagent | 智能体即工具 `registration().subAgent(...)` | [13](../agent/13-sub-agent) |
| 进程内 AgentCard 目录 | 真 A2A（`AgentScopeA2aServer` + `A2aAgent`） | [14](../agent/14-a2a) |
| 手写上下文裁剪 | `MiddlewareBase.onModelCall` | [17](../agent/17-react-runtime) |
| pendingQuestion 状态 | 外部执行工具 `externalTool(true)` 挂起续跑 | [19](../agent/19-human-in-the-loop) |
| 高危清单 + trustedTools | 权限系统 `PermissionContextState` + `ConfirmResult` | [20](../agent/20-approval-gate) |
| 四层 if 检查 | `ToolBase.checkPermissions` | [21](../agent/21-sandbox) / [25](../agent/25-cli) |
| 手写 JSONL | 订阅 `streamEvents` 写事件 + 回放投影 | [22](../agent/22-event-sourcing) |
| ToolRegistry + disposer | 工具组 `group()` / `removeToolGroups` | [11](../agent/11-tool-registry) / [23](../agent/23-plugin) |
| Hook 记录 + 手写分发 | `AgentTool` 装饰器 | [24](../agent/24-hooks) |

## 事件流 {#events}

`call()` 只给最终答案；`streamEvents()` 给出每一步的类型化事件 —— 可观测性、前端时间线、审计日志都来自它。

| AgentScope 事件 | 含义 |
| --- | --- |
| `THINKING_BLOCK_*` / 工具调用前的文本 | 思考 |
| `TOOL_CALL_*` | 工具调用 |
| `TOOL_RESULT_*` | 工具结果 |
| `EXCEED_MAX_ITERS` / `REQUIRE_USER_CONFIRM` / `REQUIRE_EXTERNAL_EXECUTION` | 守卫：熔断、等待审批、等待外部执行 |
| `AGENT_RESULT` | 最终答案 |

## 实测踩坑（2.0.4） {#pitfalls}

1. **Agent 会复制 Toolkit**：`ReActAgent.Builder.build()` 内部 `toolkit.copy()`，运行期增删工具要用 `agent.getToolkit()`。（11/23）
2. **工具组激活是会话级状态**：分组工具需要 `getAgentState(ctx).getToolContext().addActivatedGroups(...)` 才对模型可见。（23）
3. **权限优先级**：DENY 规则 > ASK 规则 > 工具自检 > ALLOW 规则 > 默认 ASK。显式 ASK 规则会压住会话追加的 ALLOW，「记住」就失效了。（20）
4. **MCP 远端工具默认要审批**：未声明 `readOnlyHint=true` 的 MCP 工具会触发 PERMISSION_ASKING。（09）
5. **结构化输出**：部分 OpenAI 兼容服务对 `response_format: json_schema` 支持不完整，`nativeStructuredOutput(false)` 改走工具路径更稳。（06/12/16）
6. **A2A 名片地址**：SDK 用 `URI.resolve` 拼接，多个代理挂在同主机不同路径时，base 以 `/` 结尾、相对路径不以 `/` 开头。（14）
7. **手工构造 ToolUseBlock**：参数校验看的是 `content`（原始 JSON），`input` 与 `content` 都要填。
8. **compaction 默认摘要是英文**：中文场景用 `summaryPrompt(...)` 自定义（`{messages}` 占位符）。（05）
9. **Middleware 改不了工具结果**：`onActing` 可改参数，但结果由框架内部收集；按工具改写结果用 `AgentTool` 装饰器。（24）
10. **HarnessAgent 默认把会话状态写到 `~/.agentscope/state/`**：教学场景显式 `.stateStore(new InMemoryAgentStateStore())`。（05/18）

## 资料 {#links}

- 官方文档：<https://java.agentscope.io>（官网可能已是更新版本的内容，2.0.4 以仓库 `v2.0.4` tag 下的 docs 为准）
- 本仓库：[ToyAgentScope README](https://github.com/wangJiaLun-china/ai_learn/tree/main/ToyAgentScope)
