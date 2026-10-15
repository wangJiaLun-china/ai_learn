# ToyAgentScope

> ToyAgent 的 **AgentScope Java 2.0** 姊妹版：同样的 26 个场景、同样的 `Agent.chat()` 契约、同样的测试页面，
> 内部从「全部手写」换成「装配 AgentScope 组件」。对照阅读，看清框架替你做了什么、又把什么留给了你。

Java 17 · Gradle · AgentScope Java **2.0.4**（GA）· 只用真实模型（OpenAI 兼容接口）。

- 手写版对照：[`../ToyAgent`](../ToyAgent)（`toyagent` ↔ `toyagentscope` 子包结构一一对应，可对照 diff）
- 框架文档：<https://java.agentscope.io>（注意：官网已在写 2.1 的内容，2.0.4 请以仓库 `v2.0.4` tag 下的 docs 为准）

## 快速开始

```bash
# 1. 配置模型（任意 OpenAI 兼容接口：DeepSeek / 通义 / 智谱 / 火山方舟…）
cp config.properties.example config.properties   # 填 base-url / api-key / model

# 2. 启动（默认 8099，可用 TOY_AGENT_PORT 覆盖）
./gradlew run
# 打开 http://localhost:8099

# 3. 打包成单个可执行 jar
./gradlew shadowJar
java -jar build/libs/ToyAgentScope.jar

# Step25 的终端形态
./gradlew -q runCli                          # REPL
./gradlew -q runCli --args='-p 运行：ls'      # 单命令模式
```

也可以用环境变量配置默认模型：`LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL`（兼容 `OPENAI_*`）。
页面右上角可以为「本浏览器」单独配置模型，配置只存在浏览器 localStorage，随请求携带。

## 26 个场景：手写零件 → AgentScope 组件

| # | 场景 | 接口类 | AgentScope 2.0 实现要点 |
|:---:|------|--------|------|
| 01 | 对话 | `ChatAgent` | `ReActAgent` 不带工具；每次新 sessionId = 无记忆 |
| 02 | 提示词 | `PromptAgent` | `sysPrompt(...)` 三层结构 |
| 03 | ReAct | `ReActAgent` | `ReActAgent` + `Toolkit` + `@Tool` + `maxIters`，Function Calling 取代 JSON 协议 |
| 04 | 工具调用 | `ToolCallAgent` | 拆开看：`Model.stream` + `ReasoningContext` + `toolkit.callTool` |
| 05 | 记忆 | `MemoryAgent` | AgentState 会话上下文 + `HarnessAgent` `compaction` 自动压缩 |
| 06 | 意图路由 | `RouterAgent` | 结构化输出 `call(msgs, Intent.class, ctx)` |
| 07 | RAG | `RagAgent` | Agentic RAG：检索做成 `@Tool`，模型决定何时检索 |
| 08 | LLM-Wiki | `WikiAgent` | `wiki_search` / `wiki_compile` 两个工具，Markdown 持久化 |
| 09 | MCP | `McpAgent` | **真 MCP**：官方 SDK stdio Server 子进程 + `McpClientBuilder` + `registerMcpClient` |
| 10 | Skills | `SkillAgent` | `SKILL.md` + `ClasspathSkillRepository`，L0/L1/L2 渐进披露 |
| 11 | 工具注册表 | `RegistryAgent` | `Toolkit` 注册 / `getToolSchemas` 发现 / `removeTool` 热注销 |
| 12 | 多智能体 | `MultiAgent` | 4 个 ReActAgent 普通编排（2.0 已移除 Pipeline/MsgHub）+ 结构化审查 |
| 13 | 子代理 | `SubagentAgent` | spawn：`registration().subAgent(...)`；fork：读父会话注入子代理 |
| 14 | A2A | `A2AAgent` | **真 A2A**：`AgentScopeA2aServer` 挂在 `/a2a/{name}`，`A2aAgent` 通过 Agent Card 回环调用 |
| 15 | Loop 运行时 | `LoopAgent` | Java 守卫 + `maxIters` 保险丝 + `maxRetries` / `fallbackModel` 兜底 |
| 16 | 工作流 | `WorkflowAgent` | 显式状态机：结构化路由 + `callTool` 节点 + agent 节点 |
| 17 | ReAct 运行时 | `RuntimeAgent` | `MiddlewareBase.onModelCall` 裁剪上下文 + `GenerateReason` → `TurnEndReason` |
| 18 | 全流程 | `FullAgent` | HarnessAgent + `onSystemPrompt` 注入画像 + 压缩 + 守卫 |
| 19 | 人工介入 | `AskAgent` | `externalTool(true)` 挂起（TOOL_SUSPENDED），工具结果回填续跑 |
| 20 | 审批门禁 | `ApprovalAgent` | 权限系统：ALLOW 白名单 + 默认 ASK，`ConfirmResult` 批准/拒绝/会话免审，超时 DENY |
| 21 | 沙箱 | `SandboxAgent` | `ToolBase.checkPermissions` 四层拦截 |
| 22 | 事件溯源 | `EventSourcedAgent` | 订阅 `streamEvents` 追加 JSONL，每轮回放投影 + 全新 session |
| 23 | 插件 | `PluginAgent` | 子优先 ClassLoader + 工具组整组注册/注销 + 会话激活工具组 |
| 24 | Hooks | `HooksAgent` | `AgentTool` 装饰器：before 审计/风控，after 脱敏 |
| 25 | CLI | `CliAgent` | REPL / `-p` + `run_command`（checkPermissions + ProcessBuilder 真执行） |
| 26 | 定时 | `ScheduleAgent` | 调度做成 5 个 `@Tool`，触发时调提醒智能体，Jackson 写 JSONL |

## 核心胶水：事件流 → 页面轨迹

页面与 HTTP 契约和 ToyAgent 完全一致（`POST /api/{stepId}/chat` 返回 `{answer, trace[]}`）。
框架接管了 ReAct 循环后，轨迹不再「顺手记录」，而是由 [`Tracer`](src/main/java/com/wangjialun/ai/toyagentscope/agent/support/Tracer.java)
订阅 AgentScope 2.0 的类型化事件流翻译而来：

| AgentScope 事件 | 轨迹节点 |
|---|---|
| `THINKING_BLOCK_*` / 工具调用前的文本 | `thought` |
| `TOOL_CALL_*` | `action`（`name(args)`） |
| `TOOL_RESULT_*` | `observation`（可按工具名改成 `retrieve` / `mcp` / `skill`…） |
| `EXCEED_MAX_ITERS` / `REQUIRE_USER_CONFIRM` / `REQUIRE_EXTERNAL_EXECUTION` | `guard` |
| `AGENT_RESULT` | `final` |

## 实测踩坑（AgentScope 2.0.4）

1. **Agent 会复制 Toolkit**：`ReActAgent.Builder.build()` 内部 `toolkit.copy()`，运行期增删工具要用 `agent.getToolkit()`。（Step11/23）
2. **工具组激活是会话级状态**：分组工具需要 `getAgentState(ctx).getToolContext().addActivatedGroups(...)` 才对模型可见。（Step23）
3. **权限优先级**：DENY 规则 > ASK 规则 > 工具自检 > ALLOW 规则 > 默认 ASK。显式 ASK 规则会压住会话追加的 ALLOW，「记住」就失效了。（Step20）
4. **MCP 远端工具默认要审批**：未声明 `readOnlyHint=true` 的 MCP 工具会触发 PERMISSION_ASKING。（Step09）
5. **结构化输出**：部分 OpenAI 兼容服务对 `response_format: json_schema` 支持不完整，`nativeStructuredOutput(false)` 改走工具路径更稳。（Step06/12/16）
6. **A2A 名片地址**：SDK 用 `URI.resolve` 拼接，多个代理挂在同主机不同路径时，base 以 `/` 结尾、相对路径不以 `/` 开头。（Step14）
7. **手工构造 ToolUseBlock**：参数校验看的是 `content`（原始 JSON），`input` 与 `content` 都要填。（`support/ToolCalls`）
8. **compaction 默认摘要是英文**：中文场景用 `summaryPrompt(...)` 自定义（`{messages}` 占位符）。（Step05）
9. **Middleware 改不了工具结果**：`onActing` 可改参数，但结果由框架内部收集；按工具改写结果用 `AgentTool` 装饰器。（Step24）
10. **HarnessAgent 默认把会话状态写到 `~/.agentscope/state/`**：`disableSessionPersistence()` 管不到它，教学场景显式 `.stateStore(new InMemoryAgentStateStore())`。（Step05/18）

## 项目结构

```
ToyAgentScope/
├── build.gradle.kts / settings.gradle.kts / gradlew   # Gradle + Shadow（fat jar，合并 SPI 服务文件）
├── config.properties.example                          # 模型配置模板（config.properties 不入库）
├── web/                                               # 复用 ToyAgent 的测试页（文案与文章已改为 AgentScope 版）
├── data/                                              # 运行时数据：wiki / events / sandbox / plugins / workspace（不入库）
└── src/main/
    ├── java/com/wangjialun/ai/toyagentscope/
    │   ├── Application.java        # JDK HttpServer：静态页 + /api + /source/{step} + /a2a/{name}
    │   ├── Scenarios.java          # 26 个场景工厂注册表
    │   ├── llm/Models.java         # OpenAIChatModel 装配（config.properties / LLM_* / 浏览器配置）
    │   └── agent/
    │       ├── Agent.java          # 契约：chat / trace / reset
    │       ├── support/            # Tracer、TraceLog、CommonTools、ToolCalls、Texts、Json
    │       └── step01..step26/     # 一个场景一个接口类
    └── resources/skills/           # Step10 的 SKILL.md
```

## 冒烟测试

```bash
./gradlew run &                     # 或 TOY_AGENT_PORT=8199 ./gradlew run
bash scripts/smoke.sh 8099          # 依次跑 26 个场景的示例问题，检查 answer / trace
```

## 说明

个人 AI 学习项目 · 王加伦（wangjialun）
