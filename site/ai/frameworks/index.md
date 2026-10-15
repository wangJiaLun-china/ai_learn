# 框架生态对比

> 板块：框架生态 · 手写版讲清每个零件，框架把零件装配好。先读懂 26 个场景，再看框架，每个抽象都能对上号。

## 横向对比 {#compare}

| | AgentScope Java | LangChain / LangGraph | Spring AI | Claude Agent SDK |
| --- | --- | --- | --- | --- |
| 语言 | Java | Python / JS（Java 生态有 LangChain4j） | Java（Spring Boot） | Python / TypeScript |
| 定位 | 多智能体框架，事件流 + Harness | 组件库 + 图编排运行时 | Spring 风格的 AI 接入层 | Claude Code 同款的 Agent 运行时 |
| 编排模型 | ReActAgent 循环 + Middleware | 显式状态图（节点 + 条件边） | ChatClient + Advisor 链 | 内置 Agent Loop，配置驱动 |
| 工具 | `@Tool` + `Toolkit` | `@tool` / Tool 类 | `@Tool` 方法 | 内置文件/命令工具 + 自定义工具 |
| MCP | 支持 | 支持（适配器） | 支持 | 原生支持 |
| 多智能体 | 智能体即工具、A2A | 多图 / 子图、Supervisor | 需自行编排 | 子代理（subagents） |
| 人机协同 | 外部执行工具挂起、权限系统 | `interrupt` + checkpointer | 需自行实现 | 权限模式 + 工具审批回调 |
| 适合 | Java 团队做完整 Agent 应用 | 需要精确控制流程的复杂编排 | 已有 Spring 应用接入 LLM 能力 | 快速做出能读写文件、跑命令的通用 Agent |

## 怎么选 {#choose}

- **已经是 Spring 项目，只想加问答 / RAG / 少量工具** → Spring AI
- **Java 团队要做完整 Agent（多工具、多智能体、权限、事件流）** → AgentScope Java
- **流程固定、要求强可控、要断点续跑** → LangGraph 的状态图（对应 [16 工作流](../agent/16-workflow)）
- **要一个「会干活」的通用 Agent（读代码、改文件、跑命令）** → Claude Agent SDK

## 与 26 场景的对应 {#scenes}

| 能力 | 手写场景 | 框架里叫什么 |
| --- | --- | --- |
| ReAct 循环 | [03](../agent/03-react) / [17](../agent/17-react-runtime) | ReActAgent · AgentExecutor / create_react_agent · ChatClient 工具循环 · SDK 内置 loop |
| 工具 | [04](../agent/04-tool-call) / [11](../agent/11-tool-registry) | `@Tool` / `Toolkit` · `@tool` · `@Tool` · 自定义工具 / MCP |
| 记忆压缩 | [05](../agent/05-memory) | compaction · memory / checkpointer · ChatMemory Advisor · 自动上下文压缩 |
| 工作流 | [16](../agent/16-workflow) | 显式状态机 · StateGraph · — · — |
| 子代理 | [13](../agent/13-sub-agent) | subAgent 注册 · 子图 · — · subagents |
| 审批 | [20](../agent/20-approval-gate) | 权限系统 · interrupt · — · 权限模式 / 回调 |
| Hooks | [24](../agent/24-hooks) | AgentTool 装饰器 / Middleware · callbacks · Advisor · hooks |

## 各框架笔记 {#pages}

- [AgentScope Java](./agentscope) —— 本仓库 ToyAgentScope 实际使用，含实测踩坑
- [LangChain / LangGraph](./langgraph)
- [Spring AI](./spring-ai)
- [Claude Agent SDK](./claude-agent-sdk)
