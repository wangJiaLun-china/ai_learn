package com.wangjialun.ai.practice.step17;

import com.wangjialun.ai.practice.kit.Arithmetic;
import com.wangjialun.ai.practice.kit.CityWeather;
import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.step11.ToolDefinition;
import com.wangjialun.ai.practice.step11.ToolRegistry;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step17 · ReAct 运行时。一次 chat 是一个 turn，turn 里的循环是 step。
 * 调模型前按预算裁剪历史；循环只能以 TurnEndReason 收场。
 */
public interface RuntimeAgent extends Agent {

    int MAX_STEPS_PER_TURN = 6;
    int BUDGET_TOKENS = 100;
    int KEEP_MIN = 4;

    class Impl implements RuntimeAgent {

        private final ChatModel model;
        private final ToolRegistry registry = new ToolRegistry();
        /** 跨轮历史，裁剪直接作用在这份列表上。 */
        private final List<Message> history = new ArrayList<>();
        private final TraceLog trace = new TraceLog();
        /** 本回合最终给用户的文本。 */
        private String lastAnswer = "";

        public Impl(ChatModel model) {
            this.model = model;
            installTools();
        }

        private void installTools() {
            registry.register(new WeatherTool());
            registry.register(new CalculatorTool());
            registry.register(new EchoTool());
        }

        @Override
        public String name() {
            return "Step17 · ReAct 运行时（turn/step + 上下文裁剪 + TurnEndReason）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            history.add(Message.user(input));
            trimToBudget();
            TurnEndReason reason = runTurn();
            boolean fused = reason instanceof TurnEndReason.MaxSteps;
            String label = switch (reason) {
                case TurnEndReason.Completed completed -> "Turn 结束 · COMPLETED —— " + completed.summary();
                case TurnEndReason.MaxSteps max -> "Turn 结束 · MAX_STEPS —— 达到保险丝 " + max.steps() + " 步";
                case TurnEndReason.Error error -> "Turn 结束 · ERROR —— " + error.message();
            };
            trace.add(fused ? "guard" : "final", label, "结束原因用 TurnEndReason 表达");
            return lastAnswer;
        }

        private TurnEndReason runTurn() {
            String protocol = """
                    你使用 ReAct。可用工具：
                    """ + registry.promptCatalog() + """
                    需要工具时只输出 {"thought":"思考","action":"工具名","action_input":"参数"}。
                    信息足够时只输出 {"thought":"思考","final":"最终答案"}。
                    """;
            for (long step = 1; step <= MAX_STEPS_PER_TURN; step++) {
                List<Message> request = new ArrayList<>();
                request.add(Message.system(protocol));
                request.addAll(history);
                String raw;
                try {
                    raw = model.chat(request).trim();
                } catch (RuntimeException ex) {
                    return new TurnEndReason.Error("模型调用失败: " + ex.getMessage());
                }
                Object parsed = Texts.parseLenient(raw);
                trace.add("thought", "Step " + step + " · 思考", thoughtOf(parsed, raw));
                if (!(parsed instanceof Map<?, ?> decision)) {
                    lastAnswer = raw;
                    history.add(Message.assistant(raw));
                    return new TurnEndReason.Completed("模型直接给出答案");
                }
                String fin = Json.str(decision, "final");
                if (fin != null) {
                    lastAnswer = fin;
                    history.add(Message.assistant(raw));
                    return new TurnEndReason.Completed("模型给出最终答案");
                }
                String action = Json.str(decision, "action");
                String actionInput = Json.str(decision, "action_input");
                trace.add("action", "Step " + step + " · 行动", action + "(" + actionInput + ")");
                String observation = registry.lookup(action).map(tool -> {
                    try {
                        return tool.execute(Map.of(
                                "city", actionInput == null ? "" : actionInput,
                                "expression", actionInput == null ? "" : actionInput,
                                "text", actionInput == null ? "" : actionInput));
                    } catch (Exception ex) {
                        return "工具执行失败: " + ex.getMessage();
                    }
                }).orElse("未知工具: " + action);
                trace.add("observation", "Step " + step + " · 观察", observation);
                history.add(Message.assistant(raw));
                history.add(Message.user("Observation: " + observation));
                trimToBudget();
            }
            lastAnswer = "已达最大步数 " + MAX_STEPS_PER_TURN + "，本回合被保险丝强制终止。";
            return new TurnEndReason.MaxSteps(MAX_STEPS_PER_TURN);
        }

        private void trimToBudget() {
            if (estimate(history) <= BUDGET_TOKENS || history.size() <= KEEP_MIN) {
                return;
            }
            int dropped = 0;
            while (estimate(history) > BUDGET_TOKENS && history.size() > KEEP_MIN) {
                history.remove(0);
                dropped++;
            }
            trace.add("compress", "上下文裁剪",
                    "超过预算 " + BUDGET_TOKENS + "，丢弃最早 " + dropped + " 条，保留 " + history.size() + " 条");
        }

        private static int estimate(List<Message> messages) {
            return messages.stream().mapToInt(message -> message.content().length() / 4).sum();
        }

        private static String thoughtOf(Object parsed, String raw) {
            String thought = Json.str(parsed, "thought");
            String text = thought == null || thought.isBlank() ? raw : thought;
            int newline = text.indexOf('\n');
            return newline > 0 ? text.substring(0, newline) : text;
        }

        @Override
        public void reset() {
            history.clear();
            registry.clear();
            installTools();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }

    /** 天气工具。 */
    record WeatherTool() implements ToolDefinition {
        @Override public String name() { return "get_weather"; }
        @Override public String description() { return "查询城市天气"; }
        @Override public Map<String, String> parameters() { return Map.of("city", "城市名"); }
        @Override public String execute(Map<String, Object> args) {
            return CityWeather.query(String.valueOf(args.getOrDefault("city", "")));
        }
    }

    /** 计算工具。 */
    record CalculatorTool() implements ToolDefinition {
        @Override public String name() { return "calculator"; }
        @Override public String description() { return "四则运算"; }
        @Override public Map<String, String> parameters() { return Map.of("expression", "算式"); }
        @Override public String execute(Map<String, Object> args) {
            return Arithmetic.evalExact(String.valueOf(args.getOrDefault("expression", "")));
        }
    }

    /** 回显工具，用来把循环撑到保险丝。 */
    record EchoTool() implements ToolDefinition {
        @Override public String name() { return "echo"; }
        @Override public String description() { return "回显文本"; }
        @Override public Map<String, String> parameters() { return Map.of("text", "文本"); }
        @Override public String execute(Map<String, Object> args) {
            return "echo: " + args.getOrDefault("text", "");
        }
    }
}
