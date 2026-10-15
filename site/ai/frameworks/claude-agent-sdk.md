# Claude Agent SDK

> 板块：框架生态 · 官方定位：「把 Claude Code 作为库来构建生产级 AI Agent」—— 提供与 Claude Code 相同的工具、Agent Loop 和上下文管理，可在 Python 和 TypeScript 中编程使用。

## 核心思路 {#overview}

其他框架给你「零件」，Claude Agent SDK 直接给你一个**已经能干活的 Agent**：内置读写文件、执行命令、搜索网页等工具和成熟的 Agent Loop，你通过选项和扩展点定制它。

- 语言：Python（`claude-agent-sdk`）和 TypeScript（`@anthropic-ai/claude-agent-sdk`）；其他语言可以用 `-p` 参数把 Claude Code CLI 当子进程调用
- 入口：`query(prompt, options)`，以消息流的形式返回 Agent 每一步的输出
- 部署：SDK 只提供 Harness，运行在你自己的进程和基础设施里

## 与相近产品的区别 {#vs}

| 你想要 | 用什么 |
| --- | --- |
| 把 Claude Code 的 Agent 嵌进自己的 Python / TS 应用 | **Agent SDK** |
| 自己写工具循环、直接调 Claude API | Client SDK（可用其 tool runner 驱动循环） |
| 让 Anthropic 托管 Agent 循环和执行沙箱 | Managed Agents |
| 在终端里日常交互开发 | Claude Code CLI |

## 能力对照 {#mapping}

| 能力 | SDK 里的形态 | 对应手写场景 |
| --- | --- | --- |
| Agent Loop | 内置，规划步骤、调工具、判断何时完成 | [17 ReAct 运行时](../agent/17-react-runtime) |
| 工具 | 内置 Read / Write / Edit / Bash / Glob / Grep / WebSearch / WebFetch 等 + 自定义工具 | [04 工具调用](../agent/04-tool-call) |
| MCP | 连接外部 MCP Server | [09 MCP](../agent/09-mcp) |
| Skills | 和 Claude Code 一样从 `.claude/` 目录自动加载 | [10 Skills](../agent/10-skills) |
| 子代理 | subagents：为专门子任务派生独立 Agent | [13 子代理](../agent/13-sub-agent) |
| 上下文管理 | 接近上限时自动压缩早期历史，流里会出现 `compact_boundary` 消息 | [05 记忆](../agent/05-memory) |
| 会话 | 跨轮保持上下文，可恢复或分叉（fork） | [22 事件溯源](../agent/22-event-sourcing) |
| 权限 | 权限模式（`default` / `acceptEdits` / `plan` / `dontAsk` / `bypassPermissions` 等）+ 允许 / 禁止工具清单 + `canUseTool` 审批回调 | [20 审批门禁](../agent/20-approval-gate) / [21 沙箱](../agent/21-sandbox) |
| Hooks | `PreToolUse` / `PostToolUse` / `UserPromptSubmit` / `Stop` / `SubagentStop` / `PreCompact` / `SessionStart` 等生命周期事件 | [24 Hooks](../agent/24-hooks) |
| 插件 | 把 skills、agents、hooks、MCP server 打包，按本地路径加载 | [23 插件](../agent/23-plugin) |

::: tip 💡 Harness 的价值
同一个模型，裸调 API 和放进成熟 Harness 里，完成复杂任务的能力差别很大。这正是 [15](../agent/15-loop-runtime) ~ [24](../agent/24-hooks) 这些「工程化」场景要讲的东西。
:::

## 什么时候用 {#when}

- 需要一个能读代码、改文件、跑命令的通用 Agent（编码助手、运维、数据处理）；
- 想直接复用成熟的权限、压缩、子代理机制，而不是自己搭；
- 使用 Claude 模型，Python / TypeScript 技术栈。

## 资料 {#links}

- [Agent SDK 概览](https://code.claude.com/docs/en/agent-sdk/overview)
- [Agent Loop](https://code.claude.com/docs/en/agent-sdk/agent-loop) · [Permissions](https://code.claude.com/docs/en/agent-sdk/permissions) · [Hooks](https://code.claude.com/docs/en/agent-sdk/hooks) · [Subagents](https://code.claude.com/docs/en/agent-sdk/subagents)
