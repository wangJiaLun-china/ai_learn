package com.wangjialun.ai.practice.step22;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.kit.WorkFiles;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step22 · 事件溯源。记忆不放在内存列表里，每次对话前从 JSONL 回放投影出来。
 */
public interface EventSourcedAgent extends Agent {

    String SESSION = "SESSION_STARTED";
    String USER = "USER_MESSAGE_APPENDED";
    String AGENT = "AGENT_REPLY_APPENDED";
    String TURN = "TURN_COMPLETED";

    class Impl implements EventSourcedAgent {

        private static final String SYSTEM = """
                你的记忆来自事件日志回放。像普通助手一样回答，并记住用户告诉你的信息。
                只输出 {"final":"回答"}。
                """;

        private final ChatModel model;
        private final Path logFile = WorkFiles.resolve("events", "step22-events.jsonl");
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step22 · 事件溯源（JSONL 事件日志 + 回放投影重建会话）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            try {
                List<Map<String, Object>> existing = replay();
                trace.add("thought", "事件回放投影", "从日志重放 " + existing.size() + " 条事件");
                if (existing.isEmpty()) {
                    append(SESSION, Map.of("scenario", "step22"));
                }
                append(USER, Map.of("content", input));
                List<Message> request = new ArrayList<>();
                request.add(Message.system(SYSTEM));
                request.addAll(project(replay()));
                Object parsed = Texts.parseLenient(model.chat(request).trim());
                String answer = parsed instanceof Map<?, ?> map && Json.str(map, "final") != null
                        ? Json.str(map, "final") : String.valueOf(parsed);
                append(AGENT, Map.of("content", answer));
                append(TURN, Map.of("note", "turn-closed"));
                trace.add("final", "本轮事件已落盘", "用户消息、回答和回合结束都追加进 " + logFile.getFileName());
                return answer;
            } catch (IOException ex) {
                return "事件日志读写失败：" + ex.getMessage();
            }
        }

        @SuppressWarnings("unchecked")
        private List<Map<String, Object>> replay() throws IOException {
            ensure();
            List<Map<String, Object>> events = new ArrayList<>();
            for (String line : Files.readAllLines(logFile)) {
                if (line.isBlank()) {
                    continue;
                }
                Object parsed = Texts.parseLenient(line);
                if (parsed instanceof Map<?, ?> map) {
                    events.add((Map<String, Object>) map);
                }
            }
            return events;
        }

        private List<Message> project(List<Map<String, Object>> events) {
            List<Message> history = new ArrayList<>();
            for (Map<String, Object> event : events) {
                Object payload = event.get("payload");
                String content = payload instanceof Map<?, ?> map ? String.valueOf(map.get("content")) : "";
                switch (String.valueOf(event.get("type"))) {
                    case USER -> history.add(Message.user(content));
                    case AGENT -> history.add(Message.assistant(content));
                    default -> {
                        // 会话开始和回合结束只留在日志里，不进模型上下文
                    }
                }
            }
            return history;
        }

        private void append(String type, Map<String, Object> payload) throws IOException {
            ensure();
            long seq = Files.readAllLines(logFile).stream().filter(line -> !line.isBlank()).count() + 1;
            Map<String, Object> event = Json.obj();
            event.put("seq", seq);
            event.put("ts", System.currentTimeMillis());
            event.put("type", type);
            event.put("payload", payload);
            Files.writeString(logFile, Json.write(event) + "\n", StandardOpenOption.APPEND);
        }

        private void ensure() throws IOException {
            if (!Files.exists(logFile)) {
                Files.createDirectories(logFile.getParent());
                Files.createFile(logFile);
            }
        }

        @Override
        public void reset() {
            try {
                Files.deleteIfExists(logFile);
            } catch (IOException ignored) {
                // 下次 chat 会重新建文件
            }
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
