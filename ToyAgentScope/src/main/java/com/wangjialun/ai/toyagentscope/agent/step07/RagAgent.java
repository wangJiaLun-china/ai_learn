package com.wangjialun.ai.toyagentscope.agent.step07;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Step07 · RAG —— 检索增强生成（Agentic RAG 版）。
 * <p>
 * 手写版：每次都先检索 Top2，再把片段硬塞进提示词（Generic RAG）。
 * <p>
 * 框架版：把「检索」做成一个 @Tool，交给 ReActAgent ——
 * <b>模型自己决定要不要查、用什么关键词查、查几次</b>（Agentic RAG）。检索结果作为工具结果回到上下文，
 * 系统提示词要求「只依据检索结果作答，并标注来源编号」。
 * <p>
 * 说明：AgentScope 1.x 的 Knowledge / RAGMode 在 2.0 中已标记为弃用、正在重写，
 * 生产中可换成 agentscope-extensions-rag-* 的向量检索，骨架不变；这里用字符二元组重合度模拟相似度。
 */
public interface RagAgent extends Agent {

    /** 私有知识库：真实系统存向量库，这里用内存文档集合演示。 */
    List<String> KNOWLEDGE = List.of(
            "ToyAgentScope 是 ToyAgent 的姊妹教学项目，用 AgentScope Java 2.0 实现同样的 26 个渐进式场景，每个场景只有一个接口类。",
            "智能体 = 大模型（大脑）+ 工具（手脚）+ 记忆（延续）+ 循环（节拍）。ReAct 循环是智能体的经典执行模式。",
            "MCP（Model Context Protocol）是 Anthropic 提出的工具标准化协议，核心方法为 tools/list 与 tools/call，实现工具与智能体解耦。",
            "RAG（检索增强生成）先从知识库检索相关片段，再把片段注入提示词让模型基于事实回答，能缓解幻觉与知识过期问题。",
            "Function Calling 让模型输出结构化工具调用请求（工具名 + 参数），由外部程序执行后将结果回填给模型。",
            "上下文窗口是模型一次能处理的最大 Token 数，超出时需要用滑动窗口、摘要压缩等上下文工程手段保护关键信息。");

    /** 检索工具：按相似度取 Top2。 */
    class KnowledgeTools {

        @Tool(name = "search_knowledge", description = "在私有知识库中检索与问题相关的资料片段，返回带编号的 Top2 结果", readOnly = true)
        public String search(@ToolParam(name = "query", description = "检索关键词或问题") String query) {
            String hits = IntStream.range(0, KNOWLEDGE.size())
                    .mapToObj(i -> Map.entry(i, Texts.bigramScore(query, KNOWLEDGE.get(i))))
                    .filter(e -> e.getValue() >= 0.2)
                    .sorted(Map.Entry.<Integer, Double>comparingByValue(Comparator.reverseOrder()))
                    .limit(2)
                    .map(e -> String.format("[%d] (相似度 %.2f) %s", e.getKey() + 1, e.getValue(), KNOWLEDGE.get(e.getKey())))
                    .collect(Collectors.joining("\n"));
            return hits.isEmpty() ? "知识库中没有检索到相关内容" : hits;
        }
    }

    class Impl implements RagAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new KnowledgeTools());
            this.agent = ReActAgent.builder()
                    .name("rag")
                    .sysPrompt("""
                            你是知识库问答智能体。回答规则：
                            1. 回答前必须先调用 search_knowledge 检索；
                            2. 只依据检索到的资料回答，并标注来源编号如 [1]；
                            3. 资料不足以回答时，如实说"知识库中暂无相关内容"，禁止编造。
                            """)
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(4)
                    .build();
        }

        @Override
        public String name() {
            return "Step07 · RAG 检索增强生成";
        }

        @Override
        public String chat(String input) {
            log.clear();
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            return Tracer.of(log)
                    .observationType(tool -> "search_knowledge".equals(tool) ? "retrieve" : "observation")
                    .run(agent.streamEvents(List.of(new UserMessage(input)), ctx))
                    .text();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
