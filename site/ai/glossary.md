# 概念速查

> 每个术语一个固定锚点，代码注释可直接引用，例如 `// 📖 https://learn.wangjialun.top/ai/glossary#mcp`。新增词条按分组追加，锚点发布后不改。

## 模型层 {#model}

### Token {#token}
模型读写文本的最小单位，计费和上下文上限都按它算。→ [Token 与上下文窗口](./llm/01-token-context)

### 上下文窗口 Context Window {#context-window}
一次请求输入 + 输出的 Token 上限。模型无状态，所谓记忆就是把历史放进这个窗口。→ [Token 与上下文窗口](./llm/01-token-context#context-window)

### 上下文工程 Context Engineering {#context-engineering}
决定「什么信息、以什么形式、在什么时候」进入上下文的设计工作：提示词、历史压缩、检索注入、工具结果裁剪都属于它。→ [上下文管理](./llm/01-token-context#context-management)

### Temperature {#temperature}
控制采样随机性的参数，越低越确定。工具调用、结构化输出用低温度。→ [采样参数](./llm/02-sampling)

### 系统提示词 System Prompt {#system-prompt}
开发者写给模型的角色说明书，常按「角色 / 背景 / 任务与约束」三层组织。→ [02 提示词](./agent/02-prompt)

### Function Calling / Tool Use {#function-calling}
模型以结构化形式**提议**调用工具，程序执行后回填结果的协议。→ [Function Calling](./llm/04-function-calling)

### 结构化输出 Structured Output {#structured-output}
让模型按 JSON Schema 输出可解析的数据。→ [结构化输出](./llm/05-structured-output)

### Embedding {#embedding}
把文本编码成向量，语义相近则向量相近，是向量检索的基础。→ [Embedding 与向量检索](./llm/06-embedding)

### 幻觉 Hallucination {#hallucination}
模型一本正经地编造不存在的事实。对策：工具获取实时信息、RAG 提供依据、提示词允许拒答。→ [03 ReAct](./agent/03-react)

## 智能体内核 {#core}

### Agent 智能体 {#agent}
大模型（大脑）+ 工具（手脚）+ 记忆（延续）+ 循环（节拍）+ 工程化（兜底）。与 Chatbot 的区别在于能自主调用工具、多步推进目标。→ [知识地图](./#sections)

### ReAct {#react}
Reasoning + Acting：Thought → Action → Observation 循环，直到给出 Final Answer。几乎所有 Agent 框架的内核。→ [03 ReAct](./agent/03-react)

### Agent Loop {#agent-loop}
「调模型 → 执行工具 → 回填结果 → 再调模型」的主循环，需要步数、Token、时间等保险丝。→ [15 Loop 运行时](./agent/15-loop-runtime)、[17 ReAct 运行时](./agent/17-react-runtime)

### Budget Guard 预算守卫 {#budget-guard}
限制循环的步数 / Token / 时间，防止死循环烧钱。→ [15 Loop 运行时](./agent/15-loop-runtime)

### 记忆 Memory {#memory}
短期记忆 = 上下文中的对话历史（滑动窗口、摘要压缩）；长期记忆 = 持久化后按需检索。→ [05 记忆](./agent/05-memory)

### 意图路由 Intent Router {#intent-router}
先识别用户意图，再分发到不同处理链路的决策中枢。→ [06 意图路由](./agent/06-intent-router)

## 知识 {#knowledge}

### RAG 检索增强生成 {#rag}
切块 → 向量化 → 相似检索 → 拼进提示词生成，让模型「开卷作答」。→ [07 RAG](./agent/07-rag)

### LLM-Wiki {#llm-wiki}
让模型把零散资料编译成结构化知识条目并持久化，下次直接读条目，而不是每次重新检索原文。→ [08 LLM-Wiki](./agent/08-llm-wiki)

## 工具 {#tools}

### MCP 模型上下文协议 {#mcp}
Model Context Protocol：把工具的发现（`tools/list`）与调用（`tools/call`）标准化，工具服务与 Agent 解耦，一次实现到处可用。→ [09 MCP](./agent/09-mcp)

### Skill 技能 {#skill}
比单个工具更高层的能力包：说明文档 + 参考资料 + 脚本，按需分层加载（L0 元信息 / L1 正文 / L2 资源），节省上下文。→ [10 Skills](./agent/10-skills)

### 工具注册表 Tool Registry {#tool-registry}
统一的 ToolDefinition 协议 + 运行时注册 / 注销，工具集可动态变化。→ [11 工具注册表](./agent/11-tool-registry)

## 协作 {#collaboration}

### 多智能体 Multi-Agent {#multi-agent}
多个角色化 Agent 分工协作，典型为「规划 - 执行 - 审查」。→ [12 多智能体](./agent/12-multi-agent)

### 子代理 Subagent {#subagent}
主 Agent 动态派遣的独立上下文执行者（spawn 全新上下文 / fork 继承上下文），只回传结论，保护主上下文。→ [13 子代理](./agent/13-sub-agent)

### A2A 智能体间协议 {#a2a}
Agent-to-Agent：跨进程、跨团队的 Agent 协作协议，基于 Agent Card（描述身份、能力、技能、端点和认证要求的 JSON 元数据）发现对方，用 Task 跟踪任务状态，支持流式和异步通知。MCP 连工具，A2A 连 Agent。→ [14 A2A](./agent/14-a2a)

### 工作流 Workflow {#workflow}
用节点 + 条件边的状态机编排固定流程（LangGraph 思想），可控性强于自由 Agent Loop。→ [16 工作流](./agent/16-workflow)

## 工程化与安全 {#engineering}

### Harness {#harness}
包在模型外面的运行时外壳：循环、工具、上下文管理、权限、日志。同一个模型换个 Harness，能力差别很大。→ [18 全流程](./agent/18-full-pipeline)

### Human-in-the-Loop 人工介入 {#human-in-the-loop}
Agent 主动向人提问并挂起，拿到答复后续跑。→ [19 人工介入](./agent/19-human-in-the-loop)

### 审批门禁 Approval Gate {#approval-gate}
按风险分级，高危工具调用需人工批准后才执行。→ [20 审批门禁](./agent/20-approval-gate)

### 沙箱 Sandbox {#sandbox}
限制 Agent 执行命令 / 读写文件的范围，纵深防御多层拦截。→ [21 沙箱](./agent/21-sandbox)

### 事件溯源 Event Sourcing {#event-sourcing}
把会话的每个事件追加写入日志（如 JSONL），状态由回放投影得到，可审计、可恢复。→ [22 事件溯源](./agent/22-event-sourcing)

### 插件 Plugin {#plugin}
按约定契约打包的能力扩展，可独立加载卸载。→ [23 插件](./agent/23-plugin)

### Hook 钩子 {#hook}
在工具调用前后等生命周期节点插入的横切逻辑（拦截、脱敏、审计），与工具本身解耦。→ [24 Hooks](./agent/24-hooks)
