# Spring AI

> 板块：框架生态 · Spring 风格的 AI 接入层：用依赖注入、自动配置把模型、向量库、工具接进已有的 Spring Boot 应用。

## 核心抽象 {#concepts}

| 抽象 | 说明 | 对应手写场景 |
| --- | --- | --- |
| `ChatModel` / `ChatClient` | 统一的模型接口；`ChatClient` 是链式（fluent）API 的高层客户端，如 `chatClient.prompt().user(...).call().content()` | [01 对话](../agent/01-chat) |
| 提示词模板 | `PromptTemplate`、`system()` / `user()` | [02 提示词](../agent/02-prompt) |
| `@Tool` 方法 | 方法加 `@Tool` 注解即成工具，通过 `ChatClient` 的 `.tools(...)` 传入，框架负责调用 | [04 工具调用](../agent/04-tool-call) |
| 结构化输出 | `.entity(MyRecord.class)` 直接映射成 Java 对象 | [06 意图路由](../agent/06-intent-router) |
| Advisor | 拦截、修改、增强模型调用的链：如 `SimpleLoggerAdvisor`（日志）、`SafeGuardAdvisor`（安全） | [24 Hooks](../agent/24-hooks) |
| `ChatMemory` | 会话记忆，配合 `MessageChatMemoryAdvisor` 等 Advisor 注入历史 | [05 记忆](../agent/05-memory) |
| `VectorStore` / `EmbeddingModel` | 统一的向量库与 Embedding 接口，配合 `QuestionAnswerAdvisor` / `RetrievalAugmentationAdvisor` 做 RAG | [07 RAG](../agent/07-rag) |
| MCP | MCP Client / Server 的 Boot Starter 和 MCP 注解 | [09 MCP](../agent/09-mcp) |

## 定位 {#positioning}

- 强项：**接入**。模型、向量库厂商切换靠配置，和 Spring 生态（事务、安全、监控）无缝结合；
- 重心不在**编排**：多智能体、审批挂起、事件溯源等通常要在应用层自己实现 —— 正是 26 个场景里后半部分讲的内容。

::: info 🚀 学习建议
把 Advisor 链和 [24 Hooks](../agent/24-hooks) 对照看：本质都是在「调模型 / 调工具」前后插入横切逻辑。
:::

## 资料 {#links}

- [ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html) · [Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html) · [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Vector Databases](https://docs.spring.io/spring-ai/reference/api/vectordbs.html) · [MCP](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)
