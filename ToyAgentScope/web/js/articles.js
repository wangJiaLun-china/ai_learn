/**
 * ToyAgentScope 右侧原理文章（AgentScope Java 2.0 版）
 * 每个场景一篇：手写版做了什么 → AgentScope 怎么做 → 关键代码 → 实测踩坑
 * 代码块会被 app.js 的轻量高亮器渲染。本文件由脚本生成，代码块已做 HTML 转义。
 */
const ARTICLES = {

  home: {
    title: "ToyAgentScope：用 AgentScope Java 2.0 重走 26 个智能体场景",
    lede: "同一套 26 个场景、同一个 Agent.chat() 契约，内部从「全部手写」换成「装配 AgentScope 组件」。对照 ToyAgent 阅读，看清框架替你做了什么、又把什么留给了你。",
    chapters: [],
    html: `
<h2>一句话读懂本项目</h2>
<p>ToyAgent 用零依赖手写的方式讲清了智能体的每个零件；ToyAgentScope 保留完全相同的场景、页面与 HTTP 契约，只把 <code>chat()</code> 的内部换成 <strong>AgentScope Java 2.0.4</strong>。契约依旧只有一个方法：</p>
<pre><code data-lang="java">public interface Agent {
    // 智能体最小契约：输入一句话，输出一句话
    String chat(String input);
}</code></pre>
<div class="callout core">
  <div class="co-title">💡 框架版的三件事</div>
  每个场景的 <code>Impl</code> 只做三件事：① <strong>装配</strong>框架组件（ReActAgent / Toolkit / Middleware / 权限…）；② <strong>调用</strong> <code>agent.streamEvents(...)</code>；③ 用 <code>Tracer</code> 把<strong>类型化事件流</strong>翻译成右侧的执行轨迹。
</div>
<h2>手写零件 → 框架组件 对照表</h2>
<table>
  <tr><th>手写版 ToyAgent</th><th>AgentScope 2.0 组件</th><th>场景</th></tr>
  <tr><td>ChatModel + OpenAiChatModel（java.net.http）</td><td><code>Model</code> / <code>OpenAIChatModel</code></td><td>01</td></tr>
  <tr><td>JSON 协议 + 手写 ReAct 循环</td><td><code>ReActAgent</code> + Function Calling + <code>maxIters</code></td><td>03</td></tr>
  <tr><td>工具描述字符串 + switch 分发</td><td><code>@Tool</code> / <code>@ToolParam</code> + <code>Toolkit</code></td><td>03/04/11</td></tr>
  <tr><td>history 列表 + 摘要字符串</td><td>AgentState 会话上下文 + HarnessAgent <code>compaction</code></td><td>05/18</td></tr>
  <tr><td>“只输出 JSON”提示词 + 手动解析</td><td>结构化输出 <code>call(msgs, Record.class)</code></td><td>06/12/16</td></tr>
  <tr><td>进程内模拟 tools/list</td><td>真·MCP（官方 SDK，stdio 子进程）</td><td>09</td></tr>
  <tr><td>技能选择器 + switch</td><td><code>SKILL.md</code> + <code>ClasspathSkillRepository</code></td><td>10</td></tr>
  <tr><td>手写 dispatch_subagent</td><td>智能体即工具 <code>registration().subAgent(...)</code></td><td>13</td></tr>
  <tr><td>进程内 AgentCard 目录</td><td>真·A2A（<code>AgentScopeA2aServer</code> + <code>A2aAgent</code>）</td><td>14</td></tr>
  <tr><td>手写上下文裁剪</td><td><code>MiddlewareBase.onModelCall</code></td><td>17</td></tr>
  <tr><td>pendingQuestion 状态</td><td>外部执行工具 <code>externalTool(true)</code> 挂起续跑</td><td>19</td></tr>
  <tr><td>高危清单 + trustedTools</td><td>权限系统 <code>PermissionContextState</code> + <code>ConfirmResult</code></td><td>20</td></tr>
  <tr><td>四层 if 检查</td><td><code>ToolBase.checkPermissions</code></td><td>21/25</td></tr>
  <tr><td>手写 JSONL</td><td>订阅 <code>streamEvents</code> 写事件 + 回放投影</td><td>22</td></tr>
  <tr><td>ToolRegistry + disposer</td><td>工具组 <code>group()</code> / <code>removeToolGroups</code></td><td>11/23</td></tr>
  <tr><td>Hook 记录 + 手写分发</td><td><code>AgentTool</code> 装饰器（工具 SPI）</td><td>24</td></tr>
</table>
<h2>怎么跑起来</h2>
<pre><code data-lang="bash">cd ToyAgentScope
# 1. 配置真实模型（本项目没有 Mock）：任意 OpenAI 兼容接口
cp config.properties.example config.properties   # 填 base-url / api-key / model

# 2. 启动（JDK 17+；首次会下载依赖）
./gradlew run                 # http://localhost:8099

# 3. 打包成单个可执行 jar
./gradlew shadowJar &amp;&amp; java -jar build/libs/ToyAgentScope.jar</code></pre>
<div class="callout warn">
  <div class="co-title">⚠️ 只支持真实模型</div>
  AgentScope 版的价值就是看真模型在框架里如何思考、调用工具、被权限系统拦下 —— 所以没有 Mock。未配置模型时接口会返回「未配置模型」提示，可在右上角为本浏览器单独配置。
</div>
<h2>阅读建议</h2>
<div class="flow">
  <span class="fnode">Agent 接口</span><span class="farrow">→</span><span class="fnode">support/Tracer</span><span class="farrow">→</span><span class="fnode">step03 ReActAgent</span><span class="farrow">→</span><span class="fnode">step04 拆开 Function Calling</span><span class="farrow">→</span><span class="fnode">step20 权限系统</span><span class="farrow">→</span><span class="fnode">其余场景</span>
</div>
<p>每个场景右上角的「☕ 源码」直接打开该场景的 AgentScope 版源码；建议与 ToyAgent 同名文件并排对照（包路径完全一致）。</p>
`
  },

  step01: {
    title: "对话智能体：一个 ReActAgent，一行 call()",
    lede: "最小 MVP 从「手写 HTTP 调模型」变成「装配一个不带工具的 ReActAgent」。",
    chapters: ["ch01 大模型基础", "ch02 什么是 AI Agent", "ch03 你的第一个 Agent"],
    html: `
<h2>手写版 vs 框架版</h2>
<table>
  <tr><th></th><th>ToyAgent</th><th>ToyAgentScope</th></tr>
  <tr><td>调模型</td><td><code>model.chat(List.of(Message.user(input)))</code></td><td><code>agent.call(List.of(new UserMessage(input)), ctx)</code></td></tr>
  <tr><td>模型实现</td><td>自己拼 /chat/completions 请求</td><td><code>OpenAIChatModel</code>（流式、重试、推理内容解析）</td></tr>
  <tr><td>会话</td><td>无</td><td>按 (userId, sessionId) 隔离，每次新 sessionId = 无记忆</td></tr>
</table>
<h2>核心代码</h2>
<pre><code data-lang="java">this.agent = ReActAgent.builder()
        .name("chat")
        .model(model)          // OpenAI 兼容模型，见 llm/Models.java
        .build();

public String chat(String input) {
    // 每次新会话 → 每轮独立、没有记忆
    RuntimeContext ctx = RuntimeContext.builder()
            .userId("toy").sessionId(UUID.randomUUID().toString()).build();
    Msg reply = agent.call(List.of(new UserMessage(input)), ctx).block();
    return reply.getTextContent();
}</code></pre>
<div class="callout core">
  <div class="co-title">💡 关键概念：智能体无状态</div>
  AgentScope 2.0 删除了 1.x 的 Memory 对象。<strong>智能体实例本身不存对话</strong>，会话状态（AgentState）按 <code>(userId, sessionId)</code> 存放。同一个实例可以同时服务很多会话 —— 这是生产部署的基础。
</div>
<h2>模型层：一处装配，处处复用</h2>
<pre><code data-lang="java">OpenAIChatModel.builder()
        .baseUrl(cfg.baseUrl())     // 以 /v1、/v3 结尾都行，框架会去重版本段
        .apiKey(cfg.apiKey())
        .modelName(cfg.model())
        .stream(true)
        .formatter(new DeepSeekFormatter())   // DeepSeek 系列：正确回传 reasoning_content
        .nativeStructuredOutput(false)        // 结构化输出走工具调用（见 Step06）
        .build();</code></pre>
`
  },

  step02: {
    title: "提示词工程：sysPrompt 三层结构",
    lede: "系统提示词从「手动塞进消息列表第一条」变成 builder 上的一个属性，还能被中间件动态改写。",
    chapters: ["ch04 提示词工程"],
    html: `
<h2>三层结构</h2>
<div class="flow">
  <span class="fnode">【角色】你是谁</span><span class="farrow">→</span><span class="fnode">【背景】在什么场景</span><span class="farrow">→</span><span class="fnode">【任务与约束】做什么、怎么输出</span>
</div>
<pre><code data-lang="java">ReActAgent.builder()
        .name("prompt")
        .sysPrompt("""
            【角色】你是「ToyAgentScope」教学助手，擅长用通俗类比解释 AI 智能体概念。
            【背景】用户正在学习 AgentScope Java 2.0，基础薄弱，喜欢直白的例子。
            【任务与约束】先一句话结论，再一个生活类比；不超过 120 字；不确定就说不知道。
            """)
        .model(model)
        .build();</code></pre>
<div class="callout tip">
  <div class="co-title">🚀 进阶：动态提示词</div>
  固定提示词用 <code>sysPrompt</code>；需要每轮注入动态信息（用户画像、时间、权限）时，实现 <code>MiddlewareBase.onSystemPrompt(agent, ctx, current)</code> 返回改写后的提示词 —— Step18 用它注入用户画像。
</div>
`
  },

  step03: {
    title: "ReAct：框架就是那个循环",
    lede: "手写版 100 行的「思考-行动-观察」循环，在 AgentScope 里就是 ReActAgent 本身；我们只需订阅它的事件流。",
    chapters: ["ch04b ReAct 模式", "ch05 Agent 的思考"],
    html: `
<h2>手写版做了哪些事，框架替你做了哪些</h2>
<table>
  <tr><th>环节</th><th>ToyAgent 手写</th><th>AgentScope</th></tr>
  <tr><td>输出协议</td><td>提示词约定只输出 JSON</td><td>模型原生 Function Calling</td></tr>
  <tr><td>解析</td><td>去代码围栏 + Json.parse</td><td>框架解析 ToolUseBlock</td></tr>
  <tr><td>工具分发</td><td>switch(action)</td><td><code>Toolkit</code> 按名反射调用 @Tool 方法</td></tr>
  <tr><td>回填观察</td><td>拼一条 “Observation: …” 用户消息</td><td>ToolResultBlock 自动写回上下文</td></tr>
  <tr><td>保险丝</td><td>for 循环 MAX_STEPS</td><td><code>maxIters(5)</code> + EXCEED_MAX_ITERS 事件</td></tr>
  <tr><td>并行工具</td><td>不支持</td><td>一次推理可发起多个调用</td></tr>
</table>
<h2>核心代码</h2>
<pre><code data-lang="java">// 1. 工具：普通方法 + 注解，框架自动生成 JSON Schema
public class CommonTools {
    @Tool(name = "get_weather", description = "查询城市今天的天气", readOnly = true)
    public String getWeather(@ToolParam(name = "city", description = "城市名") String city) { ... }
}

// 2. 装配
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new CommonTools());
ReActAgent agent = ReActAgent.builder()
        .name("react").model(model).toolkit(toolkit)
        .maxIters(5)                       // 保险丝
        .build();

// 3. 运行 + 把事件流翻译成轨迹
return Tracer.of(log).run(agent.streamEvents(List.of(new UserMessage(input)), ctx)).text();</code></pre>
<h2>事件流 → 轨迹（support/Tracer.java）</h2>
<table>
  <tr><th>AgentScope 事件</th><th>轨迹节点</th></tr>
  <tr><td>THINKING_BLOCK_* / 工具调用前的文本</td><td>thought</td></tr>
  <tr><td>TOOL_CALL_START / DELTA / END</td><td>action：name(args)</td></tr>
  <tr><td>TOOL_RESULT_TEXT_DELTA / END</td><td>observation</td></tr>
  <tr><td>EXCEED_MAX_ITERS</td><td>guard：保险丝熔断</td></tr>
  <tr><td>AGENT_RESULT</td><td>final</td></tr>
</table>
<div class="callout core">
  <div class="co-title">💡 为什么订阅事件而不是 call()</div>
  <code>call()</code> 只给最终答案；<code>streamEvents()</code> 给出每一步的类型化事件 —— 这正是可观测性、前端时间线、审计日志的来源。AgentScope 2.0 的头号新特性就是这套事件系统。
</div>
<div class="callout tip">
  <div class="co-title">🧪 试一试</div>
  「上海天气如何，适合出行吗，顺便算一下 12*8」—— 观察模型在<strong>同一轮</strong>里并行调用两个工具。
</div>
`
  },

  step04: {
    title: "Function Calling：把循环拆开看",
    lede: "不用 Agent，直接用 Model + Toolkit + ReasoningContext 三块积木走一遍「提议 → 执行 → 回填」。",
    chapters: ["ch03 天气查询助手", "ch10 Function Calling 与工具设计"],
    html: `
<h2>三步走</h2>
<div class="flow">
  <span class="fnode">带 ToolSchema 调模型</span><span class="farrow">→</span><span class="fnode">模型返回 ToolUseBlock</span><span class="farrow">→</span><span class="fnode">toolkit.callTool 执行</span><span class="farrow">→</span><span class="fnode">ToolResultMessage 回填</span><span class="farrow">→</span><span class="fnode">再调模型出答案</span>
</div>
<pre><code data-lang="java">// 1. 带着工具 Schema 调模型；流式分片用框架的累加器拼成完整消息
ReasoningContext acc = new ReasoningContext("tool-call");
model.stream(messages, toolkit.getToolSchemas(), GenerateOptions.builder().build())
        .doOnNext(acc::processChunk).blockLast();
Msg first = acc.buildFinalMessage();

// 2. 执行模型提议的调用
for (ToolUseBlock call : first.getContentBlocks(ToolUseBlock.class)) {
    ToolResultBlock r = toolkit.callTool(ToolCallParam.builder()
            .toolUseBlock(call).input(call.getInput()).build()).block();
    messages.add(new ToolResultMessage(call.getId(), call.getName(), Texts.textOf(r)));
}

// 3. 基于工具结果生成最终回答
Msg second = callModel(messages);</code></pre>
<div class="callout warn">
  <div class="co-title">⚠️ 踩坑：手工构造 ToolUseBlock</div>
  Toolkit 会用 JSON Schema 校验参数，校验的是 ToolUseBlock 里的<strong>原始 JSON（content）</strong>。从模型返回的调用自带 content；自己 <code>ToolUseBlock.builder()</code> 构造时只填 <code>input(Map)</code> 会报 “argument content is null” —— 见 <code>support/ToolCalls.java</code>，input 与 content 都要填。
</div>
`
  },

  step05: {
    title: "记忆系统：会话状态 + 自动压缩",
    lede: "短期记忆交给 AgentState（按 sessionId 隔离），上下文压缩交给 HarnessAgent 的 compaction。",
    chapters: ["ch04b 上下文工程", "ch06 Agent 的记忆系统"],
    html: `
<h2>两种记忆，两个组件</h2>
<table>
  <tr><th></th><th>ToyAgent</th><th>ToyAgentScope</th></tr>
  <tr><td>多轮记忆</td><td>自己维护 history 列表</td><td>同一 sessionId 连续调用，框架自动带上下文</td></tr>
  <tr><td>压缩</td><td>超过 4 轮把最早两条拼成摘要字符串</td><td><code>CompactionConfig</code>：达到阈值调用模型总结前缀，只保留最近 N 条原文</td></tr>
  <tr><td>清空</td><td>history.clear()</td><td><code>agent.clearContext(ctx)</code></td></tr>
</table>
<pre><code data-lang="java">HarnessAgent.builder()
        .name("memory").agentId("step05")
        .model(model)
        .workspace(Path.of("data", "workspace", "step05"))
        .compaction(CompactionConfig.builder()
                .triggerMessages(8)          // 8 条消息（约 4 轮）触发
                .keepMessages(4)             // 保留最近 4 条原文
                .summaryPrompt(SUMMARY_PROMPT)   // {messages} 占位符
                .build())
        // 本场景只演示记忆，关掉 harness 的其他内置能力
        .disableFilesystemTools().disableShellTool().disableWebTools()
        .disableMemoryTools().disableMemoryHooks().disableSubagents()
        .build();</code></pre>
<p>压缩后，上下文开头会出现一条名为 <code>__compaction_summary__</code> 的摘要消息（<code>ConversationCompactor.SUMMARY_MSG_NAME</code>），Tracer 据此在轨迹里标出 <code>compress</code> 节点。</p>
<div class="callout warn">
  <div class="co-title">⚠️ 实测：默认摘要是英文</div>
  CompactionConfig 的默认摘要提示词是英文的 Context Extraction 模板，中文对话也会得到英文摘要。用 <code>.summaryPrompt(...)</code> 换成中文，并要求「必须保留姓名、喜好等个人信息」。
</div>
<div class="callout tip">
  <div class="co-title">🧪 试一试</div>
  连续说 4 句个人信息（名字、颜色、城市、宠物），第 5 句问「我叫什么？我的猫叫什么？」—— 轨迹里出现 compress，答案依然正确。
</div>
`
  },

  step06: {
    title: "意图路由：结构化输出当决策中枢",
    lede: "不再要求模型「只输出 JSON」，而是给框架一个 Java record，直接拿回强类型对象。",
    chapters: ["ch06b 意图识别与决策中枢", "ch07 Agent Loop"],
    html: `
<pre><code data-lang="java">record Intent(String intent, String reason) {}   // 框架据此生成 JSON Schema

Msg m = classifier.call(List.of(new UserMessage(input)), Intent.class, ctx).block();
Intent intent = Texts.structured(m, Intent.class);   // 安全读取，拿不到返回 null

switch (intent.intent()) {
    case "weather" -&gt; CommonTools.weather(city);      // 路由到工具
    case "math"    -&gt; Calc.eval(expr);                // 路由到计算器
    default        -&gt; chatter.call(...);              // 路由到闲聊智能体
}</code></pre>
<h2>结构化输出的两条实现路径</h2>
<table>
  <tr><th>路径</th><th>原理</th><th>适用</th></tr>
  <tr><td>原生（nativeStructuredOutput=true）</td><td>请求带 response_format: json_schema</td><td>OpenAI 官方等完整支持 json_schema 的服务</td></tr>
  <tr><td>工具（false）</td><td>临时注入 generate_response 工具，模型以调用该工具的方式交答案</td><td>大多数 OpenAI 兼容服务</td></tr>
</table>
<div class="callout warn">
  <div class="co-title">⚠️ 实测踩坑：火山方舟上原生模式不稳定</div>
  在 Ark 的 deepseek-v4.1-flash 上，原生路径多次出现字段丢失、JSON 混入解释文字甚至报 “No structured output”。改为 <code>OpenAIChatModel.builder().nativeStructuredOutput(false)</code> 走工具路径后 5/5 稳定 —— 本项目在 <code>Models.build()</code> 里统一这样配置。
</div>
`
  },

  step07: {
    title: "RAG：把检索做成工具（Agentic RAG）",
    lede: "模型自己决定查不查、用什么词查、查几次；检索结果作为工具结果回到上下文。",
    chapters: ["ch16 RAG", "ch20 检索增强生成"],
    html: `
<h2>Generic RAG vs Agentic RAG</h2>
<table>
  <tr><th></th><th>Generic（手写版）</th><th>Agentic（本场景）</th></tr>
  <tr><td>何时检索</td><td>每次都先检索</td><td>模型判断需要时调用工具</td></tr>
  <tr><td>检索词</td><td>原问题</td><td>模型改写后的关键词，可多次</td></tr>
  <tr><td>注入方式</td><td>拼进提示词</td><td>ToolResultBlock 回填</td></tr>
</table>
<pre><code data-lang="java">class KnowledgeTools {
    @Tool(name = "search_knowledge", description = "在私有知识库中检索相关片段，返回带编号的 Top2", readOnly = true)
    public String search(@ToolParam(name = "query", description = "检索关键词") String query) {
        // 教学：字符二元组重合度模拟向量相似度；生产换 Embedding + 向量库
        ...
    }
}
// Tracer 把该工具的结果标成 retrieve 节点
Tracer.of(log).observationType(t -&gt; "search_knowledge".equals(t) ? "retrieve" : "observation")</code></pre>
<div class="callout core">
  <div class="co-title">💡 2.0 的 RAG 现状</div>
  AgentScope 1.x 的 <code>Knowledge</code> / <code>RAGMode</code> 在 2.0 中仍可用但已标记 <em>deprecated for removal</em>，官方正在重写；扩展包 <code>agentscope-extensions-rag-simple / bailian / dify / ragflow</code> 提供现成实现。把检索包成 @Tool 是最不受 API 变动影响的写法。
</div>
<div class="callout tip">
  <div class="co-title">🧪 试一试</div>
  问「量子力学的泡利不相容原理是什么？」—— 模型会换几个关键词反复检索，确认知识库没有后如实拒答。
</div>
`
  },

  step08: {
    title: "LLM-Wiki：编译一次，持久维护",
    lede: "两个工具：wiki_search 查条目，wiki_compile 把新知识沉淀进 Markdown 文件。由模型判断用户是在提问还是要它记住。",
    chapters: ["ch13 LLM-Wiki", "ch26 知识编译与持久化"],
    html: `
<pre><code data-lang="java">@Tool(name = "wiki_compile", description = "把一条新知识编译进项目 Wiki 并持久化；用户说「编译：主题：内容」或「记住…」时调用")
public String compile(@ToolParam(name = "topic", description = "条目主题") String topic,
                      @ToolParam(name = "content", description = "条目内容") String content) {
    WikiStore.compile(topic, content);           // 写 data/wiki/user-wiki.md
    return "已编译条目【" + topic + "】";
}</code></pre>
<p>与手写版的差别：手写版用前缀「编译：」做硬路由；框架版把两个能力都交给模型，自然语言「帮我记一下发布流程…」也能触发沉淀。</p>
<div class="callout core">
  <div class="co-title">💡 与 HarnessAgent 的工作区记忆对照</div>
  HarnessAgent 的 workspace 里，<code>AGENTS.md</code> 常驻上下文、<code>MEMORY.md</code> 由记忆钩子定期整合每日笔记 —— 那是框架内置的「LLM-Wiki」。本场景用两个 @Tool 手搓同样的机制，便于看清原理。
</div>
`
  },

  step09: {
    title: "MCP：真·跨进程的工具标准",
    lede: "同一个 jar 里写一个官方 SDK 的 MCP Server，以 stdio 子进程拉起；智能体通过 tools/list、tools/call 使用它的工具。",
    chapters: ["ch07b MCP", "ch11 MCP：工具的标准化接口"],
    html: `
<div class="flow">
  <span class="fnode">McpClientBuilder 拉起子进程</span><span class="farrow">→</span><span class="fnode">initialize 握手</span><span class="farrow">→</span><span class="fnode">tools/list</span><span class="farrow">→</span><span class="fnode">registerMcpClient → 本地工具</span><span class="farrow">→</span><span class="fnode">模型调用 → tools/call</span>
</div>
<h2>服务端（官方 MCP Java SDK）</h2>
<pre><code data-lang="java">McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(json))
        .serverInfo("toy-weather-time", "1.0.0")
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        .tools(McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder().name("get_weather")
                        .inputSchema(json, "{...}")
                        .annotations(READ_ONLY)          // readOnlyHint = true
                        .build())
                .callHandler((ex, req) -&gt; text(CommonTools.weather(arg(req.arguments(), "city"))))
                .build())
        .build();</code></pre>
<h2>客户端（AgentScope 内置）</h2>
<pre><code data-lang="java">McpClientWrapper client = McpClientBuilder.create("toy-weather-time")
        .stdioTransport(javaBin, "-cp", classpath, WeatherTimeMcpServer.class.getName())
        .buildAsync().block();
toolkit.registerMcpClient(client).block();      // tools/list → 注册为本地工具</code></pre>
<div class="callout warn">
  <div class="co-title">⚠️ 实测踩坑：远端工具默认要审批</div>
  第一次跑时，模型调用 MCP 工具后直接停在 PERMISSION_ASKING。原因：AgentScope 的 <code>McpTool.checkPermissions</code> 对<strong>未声明只读</strong>的远端工具默认要求人工授权。正确做法是在 MCP Server 端给工具加注解 <code>ToolAnnotations(readOnlyHint=true)</code> —— 由工具提供方声明副作用，而不是客户端一刀切放行。
</div>
<div class="callout core">
  <div class="co-title">💡 stdout 是协议通道</div>
  stdio 传输下，MCP Server 绝不能向 stdout 打日志（本项目用 slf4j-simple，默认输出到 stderr）。
</div>
`
  },

  step10: {
    title: "Skills：SKILL.md 驱动的渐进式披露",
    lede: "技能是一个目录 + SKILL.md；选哪个技能、按什么步骤做，全由模型读说明书决定，Java 里没有 if/switch。",
    chapters: ["ch08 Skills", "ch12 Skills：工具的组合与复用"],
    html: `
<h2>L0 / L1 / L2</h2>
<table>
  <tr><th>层级</th><th>内容</th><th>何时进入上下文</th></tr>
  <tr><td>L0 元数据</td><td>SKILL.md 头部的 name + description</td><td>常驻系统提示词</td></tr>
  <tr><td>L1 说明书</td><td>SKILL.md 正文（操作步骤）</td><td>模型调用 load_skill_through_path 时</td></tr>
  <tr><td>L2 资源</td><td>references/*.md 等附件</td><td>执行到那一步才按需加载</td></tr>
</table>
<pre><code data-lang="text">src/main/resources/skills/
├── travel_plan/
│   ├── SKILL.md                 # ---\nname: travel_plan\ndescription: ...\n---\n步骤...
│   └── references/checklist.md  # L2 资源
└── weather_report/
    └── SKILL.md</code></pre>
<pre><code data-lang="java">ReActAgent.builder()
        .model(model)
        .toolkit(toolkit)                                   // get_weather / get_time
        .skillRepository(new ClasspathSkillRepository("skills"))
        .maxIters(8)
        .build();</code></pre>
<div class="callout tip">
  <div class="co-title">🧪 试一试</div>
  「帮我做一份杭州旅行攻略」：轨迹里依次出现 加载 SKILL.md → get_weather + get_time → 加载 references/checklist.md → 按说明书格式输出。打成 fat jar 后 Classpath 技能同样可用。
</div>
`
  },

  step11: {
    title: "工具注册表：Toolkit 就是注册表",
    lede: "注册 registerTool、发现 getToolSchemas、热注销 removeTool —— 下一轮推理模型就看不到被卸载的工具。",
    chapters: ["生产级框架 · ToolDefinition / ToolRegistry"],
    html: `
<pre><code data-lang="java">Toolkit toolkit = new Toolkit(ToolkitConfig.builder().allowToolDeletion(true).build());
toolkit.registerTool(new CommonTools());
ReActAgent agent = ReActAgent.builder().toolkit(toolkit)...build();

// 运行期：一定要操作 agent.getToolkit()
agent.getToolkit().getToolSchemas();          // 发现：名称 + 描述 + JSON Schema
agent.getToolkit().removeTool("get_time");    // 热注销</code></pre>
<div class="callout warn">
  <div class="co-title">⚠️ 实测踩坑：Agent 会复制 Toolkit</div>
  <code>ReActAgent.Builder.build()</code> 内部执行 <code>this.toolkit.copy()</code>。构建之后再对你手里的那个 toolkit 增删工具<strong>不会</strong>影响智能体 —— 运行期请用 <code>agent.getToolkit()</code>。另外删除默认被禁止，需要 <code>allowToolDeletion(true)</code>。
</div>
`
  },

  step12: {
    title: "多智能体：普通 Java 编排 4 个角色",
    lede: "规划者 → 研究员 → 写作者 → 审查者，各是一个独立 ReActAgent；规划和审查用结构化输出返回强类型对象。",
    chapters: ["ch10 多 Agent 系统", "ch14 多 Agent 系统架构"],
    html: `
<div class="flow">
  <span class="fnode">Planner → Plan</span><span class="farrow">→</span><span class="fnode">Researcher → 笔记</span><span class="farrow">→</span><span class="fnode">Writer → 初稿</span><span class="farrow">→</span><span class="fnode">Reviewer → Review</span><span class="farrow">→</span><span class="fnode">不通过则重写</span>
</div>
<pre><code data-lang="java">record Plan(List&lt;String&gt; steps) {}
record Review(boolean pass, String comment) {}

Plan plan = Texts.structured(planner.call(msgs, Plan.class, ctx()).block(), Plan.class);
String notes = text(researcher, "任务：" + input + "\n计划：" + plan);
String draft = text(writer, "研究笔记：\n" + notes);
Review review = Texts.structured(reviewer.call(..., Review.class, ctx()).block(), Review.class);
if (!review.pass()) draft = text(writer, "审查意见：" + review.comment() + "，请重写");</code></pre>
<div class="callout core">
  <div class="co-title">💡 2.0 移除了 Pipeline / MsgHub</div>
  AgentScope 1.x 的 <code>SequentialPipeline</code>、<code>FanoutPipeline</code>、<code>MsgHub</code> 在 2.0 中已删除。官方迁移指南的建议是：用<strong>普通代码编排 + 子代理（Step13）+ 事件流</strong>组合多智能体；并行可用 <code>Mono.zip</code>。
</div>
`
  },

  step13: {
    title: "子代理：spawn 用框架，fork 自己补",
    lede: "spawn = 把 ReActAgent 注册成工具（全新上下文）；fork = 读出父会话注入子代理（继承记忆）。",
    chapters: ["生产级框架 · SubagentRegistry / SpawnInProcessProvider / ForkInProcessProvider"],
    html: `
<pre><code data-lang="java">// spawn：智能体即工具（SubAgentTool 的参数是 session_id + message）
toolkit.registration()
        .subAgent(() -&gt; child("writer", "你是写作者子代理…"),
                  SubAgentConfig.builder().toolName("spawn_writer")
                          .description("spawn 一个写作者子代理（看不到当前对话）")
                          .forwardEvents(false).build())
        .apply();

// fork：普通 @Tool，读出父会话作为子代理的背景
@Tool(name = "fork_subagent", description = "fork 一个继承当前对话历史的子代理")
public String fork(@ToolParam(name = "task") String task) {
    String transcript = parent.getAgentState(ctx).getContext().stream()...;
    return child("forked", "你继承了父代理的对话：\n" + transcript).call(task)...;
}</code></pre>
<table>
  <tr><th></th><th>spawn</th><th>fork</th></tr>
  <tr><td>上下文</td><td>只有主代理写给它的 message</td><td>父会话全文 + 任务</td></tr>
  <tr><td>成本</td><td>低</td><td>高（带历史）</td></tr>
  <tr><td>适合</td><td>独立子任务、并行调研</td><td>需要延续用户偏好/约定的任务</td></tr>
</table>
<div class="callout tip">
  <div class="co-title">🧪 对比实验</div>
  先「记住：我最喜欢紫色」，再「fork 一个子代理写贺词」和「再 spawn 一个子代理写贺词」—— 只有 fork 出来的那句贺词带紫色。
</div>
<div class="callout core">
  <div class="co-title">💡 forwardEvents</div>
  设为 true 时子代理的事件会转发进父代理的事件流（适合做嵌套时间线）；本项目设为 false，避免子代理的 AGENT_RESULT 混入父代理结果。HarnessAgent 另有 <code>agent_spawn</code> / <code>agent_send</code> 等更完整的子代理工具集。
</div>
`
  },

  step14: {
    title: "A2A：真·跨代理协议",
    lede: "两个「远端」智能体用 AgentScopeA2aServer 发布在 /a2a/{name}；协调者通过 Agent Card 发现它们，再用 A2aAgent 走 JSON-RPC 协作。",
    chapters: ["生产级框架 · A2AController / AgentCard / CollaborationController"],
    html: `
<div class="flow">
  <span class="fnode">GET /a2a/translator/.well-known/agent-card.json</span><span class="farrow">→</span><span class="fnode">A2aAgent.call()</span><span class="farrow">→</span><span class="fnode">POST JSON-RPC message/stream</span><span class="farrow">→</span><span class="fnode">SSE 任务事件</span><span class="farrow">→</span><span class="fnode">回复</span>
</div>
<h2>服务端：把智能体发布成 A2A 服务</h2>
<pre><code data-lang="java">AgentScopeA2aServer server = AgentScopeA2aServer.builder(reactAgentBuilder)
        .agentCard(new ConfigurableAgentCard.Builder().name("translator").description("中英互译代理").build())
        .deploymentProperties(new DeploymentProperties("localhost", port, "/a2a/translator"))
        .build();
// 它不监听端口，只提供 TransportWrapper —— 由我们的 HttpServer 把请求交给它
Object result = server.getTransportWrapper("JSONRPC").handleRequest(body, headers, Map.of());
// 非流式 → JSON；流式（message/stream）→ Flux，逐条写成 SSE 的 data: 行</code></pre>
<h2>客户端：远端代理就是一个普通 Agent</h2>
<pre><code data-lang="java">A2aAgent remote = A2aAgent.builder()
        .name("translator")
        .agentCardResolver(WellKnownAgentCardResolver.builder()
                .baseUrl("http://localhost:8099/a2a/translator/")      // 注意结尾的 /
                .relativeCardPath(".well-known/agent-card.json")       // 注意开头没有 /
                .build())
        .build();
Msg reply = remote.call(new UserMessage(task)).block();</code></pre>
<div class="callout warn">
  <div class="co-title">⚠️ 实测踩坑：名片 404</div>
  A2A SDK 用 <code>URI.resolve(relativeCardPath)</code> 拼名片地址。默认相对路径 <code>/.well-known/agent-card.json</code> 以斜杠开头，会把 base 里的 <code>/a2a/translator</code> 整段丢掉，变成访问主机根路径。多个代理挂在同一主机不同路径时，base 要以 / 结尾、相对路径不能以 / 开头。
</div>
<div class="callout core">
  <div class="co-title">💡 与 Step13 的区别</div>
  子代理是「我造的、在我进程里」；A2A 的对端是<strong>别人的</strong>智能体（可能是 Python、可能是别家框架），双方只约定协议：名片 + JSON-RPC + 任务事件。
</div>
`
  },

  step15: {
    title: "Loop 运行时：守卫在外，保险丝与兜底在框架里",
    lede: "业务规则（长度、敏感词）写在 Java 层；步数熔断、失败重试、备用模型都交给 ReActAgent 的 builder。",
    chapters: ["ch08b Agent 运行时", "ch18 安全与防护", "ch21 Loop 引擎与沙箱"],
    html: `
<pre><code data-lang="java">ReActAgent.builder()
        .model(model).toolkit(toolkit)
        .maxIters(4)            // 保险丝：超过 4 轮推理强制结束（EXCEED_MAX_ITERS）
        .maxRetries(2)          // 兜底：模型调用失败自动重试
        .fallbackModel(backup)  // 兜底：主模型持续失败切换备用模型
        .build();

// 守卫：调用前后各一道
if (input.length() &gt; 200 || Texts.sensitiveHit(input) != null) return SAFE_REPLY;
Tracer.Result r = Tracer.of(log).run(agent.streamEvents(...));
if (r.is(GenerateReason.MAX_ITERATIONS)) log.add("guard", "🔥 保险丝熔断", ...);
if (Texts.sensitiveHit(r.text()) != null) return SAFE_REPLY;</code></pre>
<div class="callout core">
  <div class="co-title">💡 GenerateReason：循环为什么停</div>
  结果消息的 <code>getGenerateReason()</code> 告诉你结局：MODEL_STOP（正常）、MAX_ITERATIONS（熔断）、PERMISSION_ASKING（等审批）、TOOL_SUSPENDED（等外部输入）、INTERRUPTED（被打断）…… Step17 把它收敛成业务用的 TurnEndReason。
</div>
`
  },

  step16: {
    title: "工作流状态机：图是代码，节点是框架积木",
    lede: "classify（结构化输出）→ tool（callTool）| faq（agent）→ polish（agent）→ END。",
    chapters: ["ch11b LangGraph 与状态机", "ch15 图编排"],
    html: `
<pre><code data-lang="text">classify ──tool──▶ tool ──┐
    └─────faq────▶ faq  ──┴─▶ polish ─▶ END</code></pre>
<pre><code data-lang="java">record Route(String next, String toolName, String argument) {}

Route route = Texts.structured(classifier.call(msgs, Route.class, ctx()).block(), Route.class);
String next = "tool".equalsIgnoreCase(route.next()) ? "tool" : "faq";      // 条件边
String material = next.equals("tool")
        ? ToolCalls.callText(toolkit, route.toolName(), args)              // tool 节点：不经模型直接调工具
        : text(faq, input);                                                // faq 节点
return text(polisher, "用户问题：" + input + "\n素材：" + material);        // 汇合节点</code></pre>
<div class="callout core">
  <div class="co-title">💡 智能体 vs 工作流</div>
  Step03 由<strong>模型</strong>决定下一步；工作流由<strong>开发者</strong>画好图，模型只在节点内干活 —— 更可控、更可测。AgentScope 2.0 没有内置图引擎，显式状态机就是普通 Java 代码。
</div>
`
  },

  step17: {
    title: "ReAct 运行时：Middleware 裁剪上下文",
    lede: "turn 跨回合保留历史，step 由 maxIters 限制；ContextTrimMiddleware 在每次调模型前按 token 预算裁剪「发给模型的视图」。",
    chapters: ["生产级框架 · ReactLoopAgent / TurnEndReason"],
    html: `
<h2>Middleware 的 5 个拦截点（2.0.4）</h2>
<table>
  <tr><th>方法</th><th>时机</th><th>本项目用法</th></tr>
  <tr><td>onAgent</td><td>整个 call 前后</td><td>—</td></tr>
  <tr><td>onReasoning</td><td>每次推理</td><td>—</td></tr>
  <tr><td>onModelCall</td><td>每次调模型</td><td>Step17 上下文裁剪</td></tr>
  <tr><td>onActing</td><td>每批工具调用</td><td>（可改写参数）</td></tr>
  <tr><td>onSystemPrompt</td><td>生成系统提示词</td><td>Step18 注入画像</td></tr>
</table>
<pre><code data-lang="java">class ContextTrimMiddleware implements MiddlewareBase {
    @Override
    public Flux&lt;AgentEvent&gt; onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        Function&lt;ModelCallInput, Flux&lt;AgentEvent&gt;&gt; next) {
        if (estimate(input.messages()) &lt;= BUDGET_TOKENS) return next.apply(input);
        List&lt;Msg&gt; trimmed = dropOldestUntilWithinBudget(input.messages());   // 保留 system，从用户消息开头
        ctx.get(TraceLog.class).add("compress", "上下文裁剪", ...);
        return next.apply(new ModelCallInput(trimmed, input.tools(), input.options(), input.model()));
    }
}</code></pre>
<div class="callout warn">
  <div class="co-title">⚠️ 裁剪的两个细节</div>
  ① 只改本次发给模型的视图，会话状态里的完整历史不变；② 裁剪后第一条非 system 消息必须是真正的用户消息，不能是孤立的工具结果，否则 OpenAI 兼容接口会报错。另外注意 Middleware 签名里的 <code>Agent</code> 是 <code>io.agentscope.core.agent.Agent</code>，别和本项目的 Agent 接口混了。
</div>
<div class="callout tip">
  <div class="co-title">🧪 死循环测试</div>
  check_job 永远返回 RUNNING，模型会不断轮询，第 6 步触发 EXCEED_MAX_ITERS，TurnEndReason = MaxSteps。
</div>
`
  },

  step18: {
    title: "全流程智能体：把积木拼成一条链路",
    lede: "输入守卫 → 画像抽取 → HarnessAgent（onSystemPrompt 注入画像 + ReAct + 工具 + 压缩记忆）→ 输出守卫 → 记忆回写。",
    chapters: ["综合 Step01-17", "ch18 Agent Loop 运行时", "ch21 工业级 Harness"],
    html: `
<div class="flow">
  <span class="fnode">输入守卫</span><span class="farrow">→</span><span class="fnode">画像抽取</span><span class="farrow">→</span><span class="fnode">onSystemPrompt 注入</span><span class="farrow">→</span><span class="fnode">ReAct + 工具</span><span class="farrow">→</span><span class="fnode">maxIters</span><span class="farrow">→</span><span class="fnode">输出守卫</span><span class="farrow">→</span><span class="fnode">记忆回写</span>
</div>
<pre><code data-lang="java">HarnessAgent.builder()
        .model(model).toolkit(toolkit)
        .maxIters(5)
        .middleware(new ProfileMiddleware())          // onSystemPrompt：追加【用户画像】
        .compaction(CompactionConfig.builder().triggerMessages(12).keepMessages(6).build())
        .disableFilesystemTools().disableShellTool()...
        .build();

private class ProfileMiddleware implements MiddlewareBase {
    public Mono&lt;String&gt; onSystemPrompt(Agent a, RuntimeContext c, String current) {
        return Mono.just(profile.isEmpty() ? current : current + "\n\n【用户画像】" + profileText());
    }
}</code></pre>
<div class="callout core">
  <div class="co-title">💡 HarnessAgent = ReActAgent 全家桶</div>
  HarnessAgent 在 ReActAgent 外面加了工作区（AGENTS.md / MEMORY.md / skills/）、文件与 Shell 工具、长期记忆、子代理、计划模式、会话持久化等。教学场景按需 <code>disableXxx()</code> 关掉，只保留要演示的能力；<code>getDelegate()</code> 可拿到内部的 ReActAgent。
</div>
`
  },

  step19: {
    title: "人工介入：把人变成一个外部执行工具",
    lede: "ask_user 是 externalTool(true) 的 ToolBase：框架不在本地执行它，而是挂起并把问题交出去；人类答复后用同一个 toolCallId 回填，从断点续跑。",
    chapters: ["生产级框架 · ask / ask_user_question"],
    html: `
<div class="flow">
  <span class="fnode">模型调用 ask_user</span><span class="farrow">→</span><span class="fnode">REQUIRE_EXTERNAL_EXECUTION</span><span class="farrow">→</span><span class="fnode">TOOL_SUSPENDED 挂起</span><span class="farrow">→</span><span class="fnode">用户答复</span><span class="farrow">→</span><span class="fnode">ToolResultMessage(同一 id)</span><span class="farrow">→</span><span class="fnode">继续推理</span>
</div>
<pre><code data-lang="java">static class AskUserTool extends ToolBase {
    AskUserTool() {
        super(ToolBase.builder()
                .name("ask_user")
                .description("向用户提一个澄清问题并等待人工答复")
                .inputSchema(Map.of("type", "object",
                        "properties", Map.of("question", Map.of("type", "string")),
                        "required", List.of("question")))
                .externalTool(true));          // 关键：外部执行
    }
}

// 挂起：记住待回填的调用
if (r.is(GenerateReason.TOOL_SUSPENDED)) pending = lastToolUse(r.msg(), "ask_user");

// 续跑：答复包装成同一个 toolCallId 的工具结果
agent.streamEvents(List.of(new ToolResultMessage(pending.getId(), pending.getName(), "人工答复：" + input)), ctx);</code></pre>
<div class="callout warn">
  <div class="co-title">⚠️ 实测：提示词要“堵死”文字提问</div>
  第一版提示词只说「缺信息时调用 ask_user」，模型却直接在回复正文里提问（没走工具，也就不会挂起）。改成「不要在回复正文里直接提问 —— 必须调用 ask_user 工具」后稳定走工具。
</div>
`
  },

  step20: {
    title: "审批门禁：AgentScope 权限系统",
    lede: "只读工具配 ALLOW 白名单，其余工具默认 ASK；模型调用高危工具时框架挂起（PERMISSION_ASKING），下一条消息携带 ConfirmResult 决定放行或拒绝。",
    chapters: ["生产级框架 · approval / 权限矩阵"],
    html: `
<h2>权限引擎的判定顺序（2.0.4 源码）</h2>
<div class="flow">
  <span class="fnode">① DENY 规则</span><span class="farrow">→</span><span class="fnode">② ASK 规则</span><span class="farrow">→</span><span class="fnode">③ 工具自检 checkPermissions</span><span class="farrow">→</span><span class="fnode">④ ALLOW 规则</span><span class="farrow">→</span><span class="fnode">⑤ BYPASS 模式</span><span class="farrow">→</span><span class="fnode">⑥ 默认 ASK</span>
</div>
<pre><code data-lang="java">// 风险分级：白名单放行，其余默认询问
PermissionContextState.Builder b = PermissionContextState.builder().mode(PermissionMode.DEFAULT);
for (String t : List.of("get_weather", "calculator", "get_time")) {
    b.addAllowRule(t, new PermissionRule(t, null, PermissionBehavior.ALLOW, "policy"));
}
ReActAgent.builder()...permissionContext(b.build()).build();

// 挂起后：用户的决定放进下一条消息的 metadata
List&lt;ConfirmResult&gt; results = pending.stream().map(call -&gt; remember
        ? new ConfirmResult(true, call, List.of(new PermissionRule(call.getName(), null, PermissionBehavior.ALLOW, "session")))
        : new ConfirmResult(approve, call)).toList();
Msg resume = Msg.builder().role(MsgRole.USER).textContent("批准")
        .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results)).build();</code></pre>
<div class="callout warn">
  <div class="co-title">⚠️ 实测踩坑：「批准并记住」不生效</div>
  第一版给高危工具显式配了 ASK 规则，结果「批准并记住」后第二封邮件仍要审批。看源码才明白：<strong>ASK 规则（②）优先级高于 ALLOW 规则（④）</strong>，会话追加的 ALLOW 永远压不过它。改成「只配 ALLOW 白名单、其余走默认 ASK（⑥）」后，会话免审立即生效。
</div>
<div class="callout core">
  <div class="co-title">💡 超时默认 DENY</div>
  挂起超过 60 秒才答复，一律按拒绝处理（fail-safe）。人不在场时，宁可不做也不能误做。
</div>
`
  },

  step21: {
    title: "沙箱纵深防御：写进工具自己的 checkPermissions",
    lede: "模型每次想调用 shell_execute，框架都会先问工具「这次调用允许吗」；任一层返回 DENY，命令根本不会执行。",
    chapters: ["生产级框架 · sandbox / 危险命令拦截器"],
    html: `
<div class="flow">
  <span class="fnode">① 黑名单</span><span class="farrow">→</span><span class="fnode">② 只读白名单</span><span class="farrow">→</span><span class="fnode">③ 绝对路径边界</span><span class="farrow">→</span><span class="fnode">④ normalize 防穿越</span>
</div>
<pre><code data-lang="java">class SandboxShellTool extends ToolBase {
    @Override
    public Mono&lt;PermissionDecision&gt; checkPermissions(Map&lt;String, Object&gt; input, PermissionContextState ctx) {
        String cmd = String.valueOf(input.get("command"));
        if (DENYLIST.stream().anyMatch(cmd::contains))      return deny("① 黑名单", ...);
        if (!ALLOW_COMMANDS.contains(head(cmd)))            return deny("② 策略", ...);
        if (hasAbsolutePath(cmd))                           return deny("③ 边界", ...);
        if (!resolve(arg).startsWith(SANDBOX_ABS))          return deny("④ 规范化", ...);
        return Mono.just(PermissionDecision.allow("四层检查通过"));
    }
}</code></pre>
<p>「执行：命令」这类确定性命令由代码直接调用 <code>checkPermissions</code> 再执行，结果可复现；普通提问交给模型，模型调用时框架同样会走这四层。</p>
<div class="callout core">
  <div class="co-title">💡 第五层</div>
  生产中可再叠加 HarnessAgent 的 <code>SandboxFilesystemSpec</code>（如 <code>DockerFilesystemSpec</code>），让 Shell 跑在容器里 —— 规则拦截 + 运行隔离双保险。
</div>
`
  },

  step22: {
    title: "事件溯源：状态 = 回放事件的投影",
    lede: "每轮都用全新 sessionId（框架内存里什么都没有），先回放 JSONL 投影出历史，再连同新问题交给智能体；执行过程中的事件追加写回日志。",
    chapters: ["生产级框架 · Session Event Log / 回放投影"],
    html: `
<div class="flow">
  <span class="fnode">回放 JSONL</span><span class="farrow">→</span><span class="fnode">投影成 List&lt;Msg&gt;</span><span class="farrow">→</span><span class="fnode">全新 session 调用</span><span class="farrow">→</span><span class="fnode">listen 事件流</span><span class="farrow">→</span><span class="fnode">追加 USER / TOOL / REPLY / TURN 事件</span>
</div>
<pre><code data-lang="java">List&lt;Msg&gt; history = EventStore.replay();          // USER_MESSAGE_APPENDED → UserMessage …
EventStore.append("USER_MESSAGE_APPENDED", Map.of("text", input));
Tracer.Result r = Tracer.of(log)
        .listen(e -&gt; { if (e.getType() == AgentEventType.TOOL_CALL_END)
                           EventStore.append("TOOL_CALLED", ...); })
        .run(agent.streamEvents(concat(history, new UserMessage(input)), freshCtx));
EventStore.append("AGENT_REPLY_APPENDED", Map.of("text", r.text()));
EventStore.append("TURN_COMPLETED", Map.of("reason", r.reason()));</code></pre>
<div class="callout core">
  <div class="co-title">💡 版本说明</div>
  AgentScope 2.1 起内置 session event log（AgentSession、回放、steer/resume）；本项目锁定的 2.0.4 还没有，所以用 2.0 的事件流自己实现 —— 原理完全相同，升级后可直接对照官方实现。
</div>
`
  },

  step23: {
    title: "插件机制：隔离加载 + 工具组",
    lede: "AgentPlugin 契约 + 子优先 ClassLoader 从 data/plugins 加载插件类；插件的 @Tool 注册进一个工具组，卸载时整组移除。",
    chapters: ["生产级框架 · JavaPluginLoader / PluginToolBridgeService"],
    html: `
<pre><code data-lang="java">// 子优先：插件包内的类从插件目录加载，契约与框架类（含 @Tool 注解）仍委托宿主
protected Class&lt;?&gt; loadClass(String name, boolean resolve) {
    if (!name.startsWith(PLUGIN_PACKAGE)) return super.loadClass(name, resolve);
    Class&lt;?&gt; c = findLoadedClass(name);
    return c != null ? c : findClass(name);
}

// 工具桥接：整组注册 / 整组注销
toolkit.createToolGroup("plugin_time", "时间插件贡献的工具", true);
plugin.tools().forEach(t -&gt; toolkit.registration().tool(t).group("plugin_time").apply());
...
toolkit.removeToolGroups(List.of("plugin_time"));   // 需 allowToolDeletion(true)</code></pre>
<div class="callout warn">
  <div class="co-title">⚠️ 实测踩坑：工具注册了，模型却看不到</div>
  工具组是否对模型可见是<strong>会话级状态</strong>：推理时框架用 <code>AgentState.toolContext.activatedGroups</code> 过滤工具，新会话默认只看得到未分组的工具。调用前要为当前会话激活：<code>agent.getAgentState(ctx).getToolContext().addActivatedGroups(List.of("plugin_time"))</code>。
</div>
<div class="callout core">
  <div class="co-title">💡 为什么要子优先</div>
  标准 URLClassLoader 是父优先：插件类如果也在宿主 classpath 上，会从宿主加载，「隔离」就是假的。轨迹里能看到插件类来自「plugin-loader」，而宿主是 AppClassLoader。
</div>
`
  },

  step24: {
    title: "Hooks：用工具装饰器实现 before / after",
    lede: "HookedTool 包住真实工具：before 钩子可拦截（审计、风控），after 钩子可改写结果（脱敏）。模型调用、代码直调都会经过钩子。",
    chapters: ["生产级框架 · HookService / HookMatcher / HookOutputMerger"],
    html: `
<pre><code data-lang="java">class HookedTool implements AgentTool {          // AgentScope 的工具 SPI
    public Mono&lt;ToolResultBlock&gt; callAsync(ToolCallParam param) {
        for (Hook h : before) {
            String block = h.before().apply(param.getInput());
            if (block != null) return Mono.just(ToolResultBlock.error("被钩子拦截：" + block));
        }
        return delegate.callAsync(param).map(r -&gt; {
            String text = Texts.textOf(r);
            for (Hook h : after) text = h.after().apply(text);   // 脱敏
            return ToolResultBlock.text(text);
        });
    }
}
raw.getToolNames().forEach(n -&gt; toolkit.registerAgentTool(new HookedTool(raw.getTool(n), this)));</code></pre>
<h2>为什么不用 Middleware？</h2>
<p>读 2.0.4 源码可知：<code>onActing</code> 拿到的是 <code>ActingInput(toolCalls)</code>，能改写参数；但工具结果由框架内部的 resultHolder 收集后写回上下文，中间件改写事件流<strong>不会</strong>改变模型看到的结果。旧版 <code>Hook(PreActingEvent/PostActingEvent)</code> 能改结果，但已标记弃用。所以「按工具拦截 + 改写结果」用工具装饰器最直接；Middleware 更适合横切整个推理（见 Step17 / Step18）。</p>
<div class="callout tip">
  <div class="co-title">🧪 试一试</div>
  「查询：张三」看脱敏 → 「发送：给 13812345678 发 密码是123456」看风控拦截 → 「钩子：关」再查询，手机号裸奔 → 「审计日志」。
</div>
`
  },

  step25: {
    title: "CLI 智能体：同一个智能体，换一个壳",
    lede: "Web 里是 /api/step25/chat，终端里是 REPL 与 -p 单命令模式；run_command 用 checkPermissions 三道检查后真实执行。",
    chapters: ["ch18 CLI Agent：命令行智能助手"],
    html: `
<pre><code data-lang="bash"># REPL
./gradlew -q runCli

# 单命令模式（Claude Code 风格）
./gradlew -q runCli --args='-p 运行：ls'
java -cp build/libs/ToyAgentScope.jar com.wangjialun.ai.toyagentscope.agent.step25.CliAgent -p "列出工作区文件"</code></pre>
<pre><code data-lang="java">class RunCommandTool extends ToolBase {
    public Mono&lt;PermissionDecision&gt; checkPermissions(Map&lt;String, Object&gt; in, PermissionContextState ctx) {
        // 黑名单（rm/sudo/管道/重定向…）→ 路径边界（.. 与绝对路径）→ 白名单（ls/pwd/echo/cat/date/wc）
    }
    public Mono&lt;ToolResultBlock&gt; callAsync(ToolCallParam p) {
        // ProcessBuilder 真实执行：工作目录 data/sandbox/step25，5 秒超时
    }
}</code></pre>
<div class="callout core">
  <div class="co-title">💡 形态即壳</div>
  智能体的内核（模型 + 工具 + 权限 + 循环）不变，Web、CLI、IM 机器人只是不同的输入输出壳。HarnessAgent 的 Channel（ChatUi / 钉钉 / 飞书…）就是把这层壳标准化。
</div>
`
  },

  step26: {
    title: "定时工具：让模型自己排任务",
    lede: "schedule_once / schedule_repeat / list_jobs / cancel_job / fired_results 五个工具，由模型理解自然语言并调用；触发时再调一个提醒智能体生成文案，写入 JSONL。",
    chapters: ["生产级框架 · domain/tool/schedule"],
    html: `
<pre><code data-lang="java">@Tool(name = "schedule_once", description = "创建一次性定时任务：seconds 秒后执行 task")
public String once(@ToolParam(name = "seconds") Integer seconds, @ToolParam(name = "task") String task) {
    Job job = new Job("job-" + seq.incrementAndGet(), task, "一次性");
    job.future = scheduler.schedule(() -&gt; fire(job, true), seconds, TimeUnit.SECONDS);
    return "已创建 " + job;
}

private void fire(Job job, boolean once) {
    int n = job.fired.incrementAndGet();                         // 手写版这里忘了自增
    String text = reminder.call(...).block().getTextContent();   // 触发时再调一次智能体
    Files.writeString(TASK_LOG, Json.write(event) + "\n", CREATE, APPEND);   // Jackson：自动转义
    if (!once &amp;&amp; n &gt;= MAX_REPEAT) job.future.cancel(false);      // 循环任务上限，防止无限烧 Token
}</code></pre>
<div class="callout warn">
  <div class="co-title">⚠️ 真实模型下的成本意识</div>
  每次触发都会调一次模型。循环任务一定要设上限（本项目 5 次）和最小间隔（5 秒），否则忘了取消的任务会一直消耗 Token。
</div>
<div class="callout core">
  <div class="co-title">💡 框架的调度扩展</div>
  AgentScope 提供 <code>agentscope-extensions-scheduler-quartz / xxl-job</code>，可以按 Cron 调度整个智能体；2.0.4 只内置了 DashScope 模型配置，所以教学里用 ScheduledExecutorService 演示同样的原理。
</div>
`
  }
};
