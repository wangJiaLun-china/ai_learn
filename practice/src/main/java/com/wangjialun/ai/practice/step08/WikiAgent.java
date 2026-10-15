package com.wangjialun.ai.practice.step08;

import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.kit.WorkFiles;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step08 · LLM-Wiki。知识先编译成条目并落盘，提问时只取相关条目，不再每次重翻原文。
 */
public interface WikiAgent extends Agent {

    class Impl implements WikiAgent {

        /** 启动时就有的项目条目，主题到正文。 */
        private static final Map<String, String> BASE = Map.of(
                "架构约定", "零依赖教学项目：一个接口类对应一个智能体场景，模型统一走 ChatModel。",
                "代码规范", "使用 Java 17；核心方法用中文注释说明意图；轨迹用 type/label/detail。",
                "部署流程", "javac 编译后启动 Application，默认端口 8099，可用 TOY_AGENT_PORT 覆盖。",
                "业务规则", "未配置 API Key 时回落到 Mock 模型，轨迹仍然完整。");

        private static final String SYSTEM = """
                你是项目 Wiki 问答助手。优先依据【项目 Wiki】回答；没有条目就说明暂无，并提示用「编译：主题：内容」补充。
                """;

        private final ChatModel model;
        /** 用户用「编译：」追加的条目。 */
        private final Map<String, String> userEntries = new LinkedHashMap<>();
        private final Path file = WorkFiles.resolve("wiki", "user-wiki.md");
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
            load();
        }

        @Override
        public String name() {
            return "Step08 · LLM-Wiki 知识编译";
        }

        @Override
        public synchronized String chat(String input) {
            trace.clear();
            if (input.startsWith("编译：") || input.startsWith("编译:") || input.startsWith("记住：") || input.startsWith("记住:")) {
                return compile(input);
            }
            Map<String, String> wiki = merged();
            trace.add("load", "加载项目 Wiki",
                    "共 " + wiki.size() + " 个条目（内置 " + BASE.size() + " + 已编译 " + userEntries.size() + "）");
            List<Entry> hits = wiki.entrySet().stream()
                    .map(e -> new Entry(e.getKey(), e.getValue(), overlap(e.getKey() + e.getValue(), input)))
                    .sorted(Comparator.comparingDouble(Entry::score).reversed())
                    .limit(2)
                    .filter(entry -> entry.score() > 0)
                    .toList();
            trace.add("retrieve", "命中条目", hits.isEmpty()
                    ? "无相关条目"
                    : hits.stream().map(h -> h.topic() + "（%.0f 分）".formatted(h.score() * 100))
                    .reduce((a, b) -> a + " ｜ " + b).orElse(""));

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(SYSTEM));
            if (!hits.isEmpty()) {
                StringBuilder ctx = new StringBuilder("【项目 Wiki】\n");
                for (Entry hit : hits) {
                    ctx.append("◆ ").append(hit.topic()).append("：").append(hit.content()).append("\n");
                }
                messages.add(Message.user(ctx.toString()));
            }
            messages.add(Message.user(input));
            String answer = model.chat(messages);
            trace.add("final", "基于 Wiki 作答完成", hits.isEmpty() ? "模型按约定提示可编译新条目" : "回答基于 " + hits.size() + " 个 Wiki 条目");
            return answer;
        }

        /** 解析「编译：主题：内容」，写入内存并追加到 markdown。 */
        private String compile(String input) {
            String body = input.substring(3).trim();
            int sep = body.indexOf('：');
            if (sep < 0) {
                sep = body.indexOf(':');
            }
            if (body.isBlank() || sep <= 0 || sep == body.length() - 1) {
                trace.add("compile", "编译失败", "格式：编译：主题：内容");
                return "编译格式不对。请用：「编译：主题：内容」。";
            }
            String topic = body.substring(0, sep).trim();
            String content = body.substring(sep + 1).trim();
            boolean update = userEntries.containsKey(topic);
            userEntries.put(topic, content);
            persist();
            trace.add("compile", update ? "更新条目" : "新增条目", "◆ " + topic + "（已写入 " + file + "）");
            trace.add("final", "知识编译完成", "Wiki 现有 " + merged().size() + " 个条目");
            return "已编译进项目 Wiki：◆ " + topic + "\n\n" + content + "\n\n之后提问会带上这条知识。";
        }

        private Map<String, String> merged() {
            Map<String, String> all = new LinkedHashMap<>();
            all.putAll(BASE);
            all.putAll(userEntries);
            return all;
        }

        private void persist() {
            try {
                Files.createDirectories(file.getParent());
                StringBuilder sb = new StringBuilder("# 用户 Wiki\n\n");
                userEntries.forEach((topic, content) -> sb.append("## ").append(topic).append("\n").append(content).append("\n\n"));
                Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                trace.add("warn", "写入失败", ex.getMessage());
            }
        }

        private void load() {
            if (!Files.exists(file)) {
                return;
            }
            try {
                String topic = null;
                StringBuilder content = new StringBuilder();
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.startsWith("## ")) {
                        if (topic != null) {
                            userEntries.put(topic, content.toString().trim());
                        }
                        topic = line.substring(3).trim();
                        content.setLength(0);
                    } else if (topic != null && !line.startsWith("#")) {
                        content.append(line).append('\n');
                    }
                }
                if (topic != null) {
                    userEntries.put(topic, content.toString().trim());
                }
            } catch (IOException ignored) {
                userEntries.clear();
            }
        }

        private static double overlap(String text, String query) {
            if (query.length() < 2) {
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
        public synchronized void reset() {
            userEntries.clear();
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // 删不掉不影响下一轮从空 Wiki 开始
            }
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }

    /**
     * 一条 Wiki 命中。
     *
     * @param topic 条目标题
     * @param content 条目正文
     * @param score 与问句的重合度
     */
    record Entry(String topic, String content, double score) {
    }
}
