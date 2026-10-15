# LangChain / LangGraph

> 板块：框架生态 · LangChain 是组件库，LangGraph 是在其之上的图编排运行时。思想对应本仓库的 [16 工作流](../agent/16-workflow)。

## LangChain：组件库 {#langchain}

把 LLM 应用常用零件标准化：模型接口、提示词模板、输出解析器、文档加载与切分、向量库、检索器、工具。适合快速拼出 RAG、问答等应用。

## LangGraph：状态图 {#langgraph}

| 概念 | 说明 | 对应手写场景 |
| --- | --- | --- |
| State | 节点间共享的数据（通常是带 reducer 的字典 / TypedDict） | [16](../agent/16-workflow) 的 `Map` state |
| Node | 一个处理步骤：调模型、调工具或普通函数 | 16 的节点 |
| Edge / Conditional Edge | 固定跳转 / 根据 state 决定下一个节点 | 16 的条件边 |
| Checkpointer | 每步持久化 state，支持断点续跑、时间回溯 | [22 事件溯源](../agent/22-event-sourcing) |
| `interrupt` | 在节点内挂起等待人工输入，恢复后继续 | [19 人工介入](../agent/19-human-in-the-loop) / [20 审批门禁](../agent/20-approval-gate) |
| 子图 / Supervisor | 图嵌套，或由一个主控节点调度多个 Agent | [12 多智能体](../agent/12-multi-agent) / [13 子代理](../agent/13-sub-agent) |

::: tip 💡 Workflow vs Agent
LangGraph 的核心主张：**能写成固定流程的就别交给模型自由发挥**。确定性的部分用图结构表达，只在需要判断的节点调用模型 —— 可控、可测、可回放。ReAct Agent 本身也可以表示成一个「模型节点 ⇄ 工具节点」的两节点循环图。
:::

## 什么时候用 {#when}

- 流程复杂但相对确定，需要精确控制分支、重试、并行；
- 需要长时间运行、中途人工审批、断点恢复；
- Python / JS 技术栈。Java 生态可看 LangChain4j（组件层）。

## 本仓库对应 {#in-repo}

- [16 工作流](../agent/16-workflow)：用显式状态机实现了 LangGraph 的核心思想
