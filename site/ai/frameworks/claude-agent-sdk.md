# Claude Agent SDK

> 板块：框架生态 · 把 Claude Code 背后的 Agent 运行时（Harness）开放成 SDK：循环、工具、上下文管理、权限都已内置。

## 核心思路 {#overview}

其他框架给你「零件」，Claude Agent SDK 直接给你一个**已经能干活的 Agent**：内置读写文件、搜索、执行命令等工具和成熟的 Agent Loop，你通过配置和扩展点定制它。提供 Python 与 TypeScript 两个版本。

## 能力对照 {#mapping}

| 能力 | SDK 里的形态 | 对应手写场景 |
| --- | --- | --- |
| Agent Loop | 内置，按回合流式返回消息 | [17 ReAct 运行时](../agent/17-react-runtime) |
| 工具 | 内置文件 / 搜索 / 命令工具 + 自定义工具 | [04 工具调用](../agent/04-tool-call) |
| MCP | 原生接入 MCP Server | [09 MCP](../agent/09-mcp) |
| Skills | 文件夹形式的 `SKILL.md`，按需加载 | [10 Skills](../agent/10-skills) |
| 子代理 | subagents：独立上下文执行子任务 | [13 子代理](../agent/13-sub-agent) |
| 上下文管理 | 自动压缩长对话 | [05 记忆](../agent/05-memory) |
| 权限 | 权限模式 + 允许 / 禁止工具清单 + 审批回调 | [20 审批门禁](../agent/20-approval-gate) / [21 沙箱](../agent/21-sandbox) |
| Hooks | 工具调用前后等生命周期钩子 | [24 Hooks](../agent/24-hooks) |
| CLI 形态 | Claude Code 本身就是它的终端形态 | [25 CLI](../agent/25-cli) |

::: tip 💡 Harness 的价值
同一个模型，裸调 API 和放进成熟 Harness 里，完成复杂任务的能力差别很大。这正是 [15](../agent/15-loop-runtime) ~ [24](../agent/24-hooks) 这些「工程化」场景要讲的东西。
:::

## 什么时候用 {#when}

- 需要一个能读代码、改文件、跑命令的通用 Agent（编码助手、运维、数据处理）；
- 想直接复用成熟的权限、压缩、子代理机制，而不是自己搭；
- 使用 Claude 模型，Python / TypeScript 技术栈。

## 资料 {#links}

- 官方文档：<https://docs.anthropic.com>（Agent SDK 章节）
