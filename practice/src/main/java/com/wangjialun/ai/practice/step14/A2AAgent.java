package com.wangjialun.ai.practice.step14;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step14 · A2A。先发现名片，再按任务把信封发给外部代理，用 task_id 收回执。
 */
public interface A2AAgent extends Agent {

    /**
     * 外部代理名片。
     *
     * @param name 代理名
     * @param url 访问地址
     * @param version 协议版本
     * @param skills 技能标签
     * @param description 一句话介绍
     */
    record AgentCard(String name, String url, String version, List<String> skills, String description) {
    }

    class Impl implements A2AAgent {

        private static final Map<String, AgentCard> DIRECTORY = new LinkedHashMap<>();

        static {
            DIRECTORY.put("translator", new AgentCard("translator", "http://a2a.local/translator", "1.0.0",
                    List.of("translate"), "中英互译代理"));
            DIRECTORY.put("weather", new AgentCard("weather", "http://a2a.local/weather", "2.1.0",
                    List.of("forecast"), "天气预报代理"));
        }

        private static final String SYSTEM = """
                你是主代理，通过 A2A 与外部代理协作。
                发现名片只输出 {"tool":"a2a_discover"}。
                委派只输出 {"tool":"a2a_send","agent":"translator|weather","task":"任务描述"}。
                翻译交给 translator，天气交给 weather。拿到结果后用自然语言回答。
                """;

        private final ChatModel model;
        /** 已发出的任务号到描述。 */
        private final Map<String, String> tasks = new LinkedHashMap<>();
        private int sequence = 0;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step14 · A2A 协作（Agent Card 名片发现 · task_id 信封 · 异步回执）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(SYSTEM));
            messages.add(Message.user(input));
            for (int round = 1; round <= 3; round++) {
                String raw = model.chat(messages).trim();
                Object decision = Texts.parseLenient(raw);
                if (decision instanceof Map<?, ?> call && Json.str(call, "tool") != null) {
                    String tool = Json.str(call, "tool");
                    String result = "a2a_discover".equals(tool)
                            ? discover()
                            : send(Json.str(call, "agent"), Json.str(call, "task"));
                    messages.add(Message.assistant(raw));
                    messages.add(Message.user("工具执行结果: " + result));
                    continue;
                }
                trace.add("final", "最终回答", "汇总协作结果");
                return raw;
            }
            return "已达最大轮数，循环终止。";
        }

        private String discover() {
            trace.add("action", "a2a_discover · 名片发现", "拉取 " + DIRECTORY.size() + " 份 Agent Card");
            StringBuilder cards = new StringBuilder("[");
            int index = 0;
            for (AgentCard card : DIRECTORY.values()) {
                if (index++ > 0) {
                    cards.append(", ");
                }
                cards.append("\n  {\"name\": \"").append(card.name()).append("\", \"url\": \"").append(card.url())
                        .append("\", \"skills\": ").append(card.skills()).append("}");
            }
            trace.add("observation", "收到名片", "translator（中英互译） · weather（天气预报）");
            return cards.append("\n]").toString();
        }

        private String send(String agent, String task) {
            AgentCard card = DIRECTORY.get(agent);
            if (card == null) {
                trace.add("guard", "a2a_send · 路由失败", "目录中没有 " + agent);
                return "{\"error\": \"unknown agent: " + agent + "\"}";
            }
            String taskId = "task-" + String.format("%03d", ++sequence) + "-" + UUID.randomUUID().toString().substring(0, 4);
            tasks.put(taskId, agent + ": " + task);
            trace.add("action", "a2a_send · 发起协作", "→ " + card.name() + " @ " + card.url() + "  task=" + task);
            trace.add("thought", "状态机 · submitted → working", "对方已受理 task_id=" + taskId);
            String reply = switch (card.name()) {
                case "translator" -> "翻译结果：Hello, Agent.";
                case "weather" -> "明日晴，22-28℃，适合户外运动";
                default -> "远端已响应";
            };
            trace.add("observation", "回执 · completed", "task_id=" + taskId + " 已完成：" + reply);
            return "{\"task_id\": \"" + taskId + "\", \"status\": \"completed\", \"result\": \"" + reply + "\"}";
        }

        @Override
        public void reset() {
            tasks.clear();
            sequence = 0;
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
