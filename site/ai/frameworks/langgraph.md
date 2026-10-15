# LangChain / LangGraph

> 板块：框架生态 · 官方分工：LangChain 是 Agent 框架（模型、工具、Agent 循环的抽象与集成），LangGraph 是编排运行时（持久执行、流式、人工介入、持久化）。思想对应本仓库的 [16 工作流](../agent/16-workflow)。

## LangChain：Agent 框架 {#langchain}

- `create_agent(model=..., tools=...)` 一行得到一个带工具循环的 Agent（对应 [03 ReAct](../agent/03-react)）；
- **middleware**：在模型调用、工具调用前后插入逻辑，内置如 `SummarizationMiddleware`（历史摘要压缩）、`HumanInTheLoopMiddleware`（工具调用前人工审批）；
- 多智能体：官方给出 subagents（主 Agent 把子 Agent 当工具调用）和 handoffs（按状态切换 Agent）等模式；
- 支持 Python 和 JavaScript；Java 生态可看社区的 LangChain4j。

## LangGraph：状态图 {#langgraph}

官方定位：面向**长时运行、有状态 Agent** 的低层编排框架与运行时；可以在同一张图里混合确定性的手写步骤和由模型驱动的步骤。不依赖 LangChain 也能用。

| 概念 | 说明 | 对应手写场景 |
| --- | --- | --- |
| State | 图的共享状态（TypedDict 或 Pydantic 模型）；每个 key 有自己的 reducer，决定节点的更新如何合并（默认覆盖，也可如 `operator.add` 追加） | [16](../agent/16-workflow) 的 `Map` state |
| Node | 一个处理步骤：调模型、调工具或普通函数，返回对 State 的部分更新 | 16 的节点 |
| Edge / Conditional Edge | 固定跳转 / `add_conditional_edges` 按 state 决定下一个节点 | 16 的条件边 |
| `compile()` | 检查图结构，并挂上 checkpointer、断点等运行时参数 | — |
| Checkpointer | 每步持久化 state，支撑持久执行、断点续跑 | [22 事件溯源](../agent/22-event-sourcing) |
| `interrupt()` | 在节点内暂停并把数据交给调用方，之后用 `Command` 恢复，恢复值就是 `interrupt()` 的返回值 | [19 人工介入](../agent/19-human-in-the-loop) / [20 审批门禁](../agent/20-approval-gate) |
| 子图 | 一张图作为另一张图的节点 | [12 多智能体](../agent/12-multi-agent) / [13 子代理](../agent/13-sub-agent) |

```python
from langgraph.graph import StateGraph, MessagesState, START, END

graph = StateGraph(MessagesState)
graph.add_node(mock_llm)
graph.add_edge(START, "mock_llm")
graph.add_edge("mock_llm", END)
graph = graph.compile()
graph.invoke({"messages": [{"role": "user", "content": "hi!"}]})
```

::: tip 💡 Workflow vs Agent
LangGraph 的核心主张：**能写成固定流程的就别交给模型自由发挥**。确定性的部分用图结构表达，只在需要判断的节点调用模型 —— 可控、可测、可回放。ReAct Agent 本身也可以表示成一个「模型节点 ⇄ 工具节点」的两节点循环图。
:::

## 什么时候用 {#when}

- 只想快速做一个带工具的 Agent → LangChain `create_agent`；
- 流程复杂但相对确定，需要精确控制分支、重试、并行 → LangGraph；
- 需要长时间运行、中途人工审批、断点恢复 → LangGraph 的 checkpointer + `interrupt`。

## 本仓库对应 {#in-repo}

- [16 工作流](../agent/16-workflow)：用显式状态机实现了 LangGraph 的核心思想

## 资料 {#links}

- [LangGraph 概览](https://docs.langchain.com/oss/python/langgraph/overview) · [Graph API](https://docs.langchain.com/oss/python/langgraph/graph-api) · [Interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts)
- [LangChain Agents](https://docs.langchain.com/oss/python/langchain/agents) · [Middleware](https://docs.langchain.com/oss/python/langchain/middleware/overview) · [Multi-agent](https://docs.langchain.com/oss/python/langchain/multi-agent)
