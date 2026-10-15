package com.wangjialun.ai.practice.step07;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Message;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step07 · RAG。先按字符二元组重合度检索私有资料，再把命中片段标成【参考资料】交给模型。
 */
public interface RagAgent extends Agent {

    class Impl implements RagAgent {

        /** 私有知识条目，真实系统里它们来自向量库。 */
        private static final List<String> KNOWLEDGE = List.of(
                "ToyAgent 是智能体教学项目，用渐进场景讲清楚实现，每个场景只有一个接口类。",
                "智能体 = 大模型（大脑）+ 工具（手脚）+ 记忆（延续）+ 循环（节拍）。ReAct 是经典执行模式。",
                "MCP（Model Context Protocol）把工具标准化，核心方法是 tools/list 与 tools/call。",
                "RAG（检索增强生成）先检索知识片段，再注入提示词，让模型基于事实回答。",
                "Function Calling 让模型输出工具名和参数，由外部程序执行后把结果回填。",
                "上下文窗口有长度上限，超出后要用滑动窗口或摘要压缩保住关键信息。");

        private static final String SYSTEM = """
                你是知识库问答助手。优先依据【参考资料】回答并标注编号；资料不够就说不知道，禁止编造。
                """;

        private final ChatModel model;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step07 · RAG 检索增强生成";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            List<Hit> hits = KNOWLEDGE.stream()
                    .map(text -> new Hit(text, overlap(text, input)))
                    .sorted(Comparator.comparingDouble(Hit::score).reversed())
                    .limit(2)
                    .filter(hit -> hit.score() > 0)
                    .toList();
            trace.add("retrieve", "知识库检索", hits.isEmpty()
                    ? "未命中任何片段（得分均为 0）"
                    : hits.stream()
                    .map(hit -> "%.0f 分 · %s".formatted(hit.score() * 100, Texts.clip(hit.text(), 30)))
                    .reduce((a, b) -> a + " ｜ " + b)
                    .orElse(""));

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(SYSTEM));
            if (!hits.isEmpty()) {
                StringBuilder ctx = new StringBuilder("【参考资料】\n");
                for (int i = 0; i < hits.size(); i++) {
                    ctx.append("[").append(i + 1).append("] ").append(hits.get(i).text()).append("\n");
                }
                messages.add(Message.user(ctx.toString()));
            }
            messages.add(Message.user(input));
            String answer = model.chat(messages);
            trace.add("final", "增强生成完成", hits.isEmpty() ? "模型按约定拒答" : "回答基于 " + hits.size() + " 个知识片段");
            return answer;
        }

        /** 问句的二字片段有多少出现在文档里，用来代替向量相似度。 */
        private static double overlap(String text, String query) {
            if (query == null || query.length() < 2) {
                return 0;
            }
            Set<String> grams = new HashSet<>();
            for (int i = 0; i < query.length() - 1; i++) {
                grams.add(query.substring(i, i + 2));
            }
            long hit = grams.stream().filter(text::contains).count();
            return grams.isEmpty() ? 0 : (double) hit / grams.size();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }

    /**
     * 一条检索命中。
     *
     * @param text 知识原文
     * @param score 与问句的重合度，0 到 1
     */
    record Hit(String text, double score) {
    }
}
