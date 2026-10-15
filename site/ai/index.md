# AI 知识地图

智能体不是玄学，它就是一个**会调用大模型的程序**。整个仓库的灵魂只有一个方法签名：

```java
public interface Agent {
    // 智能体最小契约：输入一句话，输出一句话
    String chat(String input);
}
```

::: tip 💡 核心公式
智能体 = 大模型（大脑）+ 工具（手脚）+ 记忆（延续）+ 循环（节拍）+ 工程化（兜底）。26 个场景，就是这个公式逐项展开的过程。
:::

## 四个板块 {#sections}

| 板块 | 回答的问题 | 入口 |
| --- | --- | --- |
| 一、LLM 基础 | 大模型 API 到底收什么、吐什么？Agent 依赖它的哪些能力？ | [Token 与上下文窗口](./llm/01-token-context) |
| 二、Agent 26 场景 | 一种能力怎么用一个接口类实现？手写版和框架版差在哪？ | [01 对话](./agent/01-chat) |
| 三、框架生态 | 生产里用哪个框架？各自的编排模型是什么？ | [对比总览](./frameworks/) |
| 概念速查 | 这个术语一句话是什么意思？ | [概念速查](./glossary) |

## 推荐学习路线 {#route}

1. **LLM 基础 01 → 04**：先弄清 messages、上下文窗口和 Function Calling 协议，后面所有场景都建立在它们之上。
2. **Agent 场景 01 → 04**：对话 → 提示词 → ReAct → 工具调用，掌握智能体的最小内核。
3. **场景 15 运行时**：守卫、保险丝、异常兜底，理解「能跑」到「能上线」的差距。
4. **其余场景按分组读**：记忆与知识（配合 LLM 基础 06）→ 工具的进化 → 协作与派遣 → 人机协同与安全 → 扩展与形态。
5. **框架生态**：带着手写版的理解看 AgentScope 等框架，每个抽象都能对上号。

读源码时，每个场景先看接口注释里的「一句话定位」，再看 `chat()` 方法 —— 所有场景的复杂度都藏在这一个方法里。

## 26 个场景的演进地图 {#scenes}

对应仓库里的 26 个渐进式场景：[ToyAgent](https://github.com/wangJiaLun-china/ai_learn/tree/main/ToyAgent)（手写）、[practice](https://github.com/wangJiaLun-china/ai_learn/tree/main/practice)（练习）、[ToyAgentScope](https://github.com/wangJiaLun-china/ai_learn/tree/main/ToyAgentScope)（AgentScope 版）。

| 分组 | 场景 | 接口类 | 新增能力 |
| --- | --- | --- | --- |
| 基础内核 | [01 对话](./agent/01-chat) | `ChatAgent` | 最小 MVP：接通模型 |
| | [02 提示词](./agent/02-prompt) | `PromptAgent` | 系统提示词三层结构 |
| | [03 ReAct](./agent/03-react) | `ReActAgent` | 思考-行动-观察循环 |
| | [04 工具调用](./agent/04-tool-call) | `ToolCallAgent` | Function Calling |
| 记忆与知识 | [05 记忆](./agent/05-memory) | `MemoryAgent` | 多轮记忆 + 上下文压缩 |
| | [06 意图路由](./agent/06-intent-router) | `RouterAgent` | 意图识别 + 决策中枢 |
| | [07 RAG](./agent/07-rag) | `RagAgent` | 检索增强生成 |
| | [08 LLM-Wiki](./agent/08-llm-wiki) | `WikiAgent` | 知识编译与持久化 |
| 工具的进化 | [09 MCP](./agent/09-mcp) | `McpAgent` | 工具标准化协议 |
| | [10 Skills](./agent/10-skills) | `SkillAgent` | 技能编排 L0/L1/L2 |
| | [11 工具注册表](./agent/11-tool-registry) | `RegistryAgent` | ToolDefinition 协议 + 注册/注销 |
| 协作与派遣 | [12 多智能体](./agent/12-multi-agent) | `MultiAgent` | 规划-执行-审查协作 |
| | [13 子代理](./agent/13-sub-agent) | `SubagentAgent` | spawn / fork 动态派遣，独立上下文 |
| | [14 A2A](./agent/14-a2a) | `A2AAgent` | Agent Card 发现 + task_id 信封 + 异步回执 |
| 运行时与工程化 | [15 Loop 运行时](./agent/15-loop-runtime) | `LoopAgent` | 守卫 + 保险丝 + 异常兜底 |
| | [16 工作流](./agent/16-workflow) | `WorkflowAgent` | 状态机编排（LangGraph 思想） |
| | [17 ReAct 运行时](./agent/17-react-runtime) | `RuntimeAgent` | turn/step 循环 + 上下文裁剪 + TurnEndReason |
| | [18 全流程](./agent/18-full-pipeline) | `FullAgent` | 守卫 + 记忆 + ReAct + 工具一条链路 |
| 人机协同与安全 | [19 人工介入](./agent/19-human-in-the-loop) | `AskAgent` | 提问挂起 → 答复续跑 |
| | [20 审批门禁](./agent/20-approval-gate) | `ApprovalAgent` | 风险分级 + 审批挂起 + 会话免审 |
| | [21 沙箱](./agent/21-sandbox) | `SandboxAgent` | 纵深防御四层拦截 |
| | [22 事件溯源](./agent/22-event-sourcing) | `EventSourcedAgent` | JSONL 事件流 + 回放投影 |
| 扩展与形态 | [23 插件](./agent/23-plugin) | `PluginAgent` | 插件契约 + 隔离 ClassLoader |
| | [24 Hooks](./agent/24-hooks) | `HooksAgent` | before 拦截 / after 脱敏 |
| | [25 CLI](./agent/25-cli) | `CliAgent` | 终端 REPL + 沙箱执行器 |
| | [26 定时工具](./agent/26-scheduler) | `ScheduleAgent` | 一次性/循环调度，结果事件落盘 |

## 与《AI Agent 通识教程》的对应 {#tutorial}

26 个场景覆盖了教程中「能用一个接口类实现」的部分。偏平台与工程化的章节，对应手段已内嵌在场景实现里：

- **ch09 提示词工程 / ch22 Harness** → 场景 02 的三层提示词 + 场景 15 的运行时外壳
- **ch13 Dify/Coze 可视化编排** → 场景 16 的节点 + 条件边就是可视化编排的后端本质
- **ch14 CLI Agent** → 场景 25；**ch15 GUI Agent** → 工具侧换成浏览器执行器，骨架同场景 04
- **ch17 评估 / ch19 部署 / ch20 推理框架** → 模型换 Ollama / vLLM 端点即可（改 `config.properties`）
- **ch23-27 展望篇** → 读完 26 个场景再回头看会非常轻松

每篇场景笔记页末的「对应教程」列出具体章节。
