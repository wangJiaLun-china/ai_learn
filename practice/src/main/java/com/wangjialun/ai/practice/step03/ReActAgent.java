package com.wangjialun.ai.practice.step03;

import com.wangjialun.ai.practice.kit.Arithmetic;
import com.wangjialun.ai.practice.kit.CityWeather;
import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step03 · ReAct。模型每轮只做一个决定：继续调用工具，或者给出最终答案。
 * 观察结果用「Observation:」回喂，Mock 看到这个标记才会收束。
 */
public interface ReActAgent extends Agent {

    int MAX_STEPS = 5;

    class Impl implements ReActAgent {

        private static final String PROTOCOL = """
                你使用 ReAct 解决问题。每轮只输出一个 JSON：
                1. {"thought":"...","action":"工具名","action_input":"参数"}
                2. {"thought":"...","final":"最终答案"}
                可用工具：get_weather(city)、calculator(expression)、get_time()。
                """;

        private final ChatModel model;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step03 · ReAct 智能体（思考-行动-观察循环）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(PROTOCOL));
            messages.add(Message.user(input));

            for (int step = 1; step <= MAX_STEPS; step++) {
                String raw = Texts.stripFence(model.chat(messages));
                Object parsed = Texts.parseLenient(raw);
                if (!(parsed instanceof Map<?, ?> decision)) {
                    trace.add("final", "Final Answer", raw);
                    return raw;
                }
                trace.add("thought", "Thought " + step, text(decision, "thought"));
                String fin = Json.str(decision, "final");
                if (fin != null) {
                    trace.add("final", "Final Answer", fin);
                    return fin;
                }
                String action = Json.str(decision, "action");
                String actionInput = Json.str(decision, "action_input");
                trace.add("action", "Action " + step, action + "(" + actionInput + ")");
                String observation = runTool(action, actionInput);
                trace.add("observation", "Observation " + step, observation);
                messages.add(Message.assistant(raw));
                messages.add(Message.user("Observation: " + observation));
            }
            String stop = "已达最大步数 " + MAX_STEPS + "，循环终止。";
            trace.add("guard", "达到最大步数", stop);
            return stop;
        }

        private String runTool(String action, String input) {
            return switch (action == null ? "" : action) {
                case "get_weather" -> CityWeather.query(input);
                case "calculator" -> Arithmetic.evalExact(input);
                case "get_time" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                default -> "未知工具: " + action;
            };
        }

        private static String text(Map<?, ?> decision, String key) {
            String value = Json.str(decision, key);
            return value == null ? "" : value;
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
