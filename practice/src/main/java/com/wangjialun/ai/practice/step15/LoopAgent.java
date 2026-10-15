package com.wangjialun.ai.practice.step15;

import com.wangjialun.ai.practice.kit.Arithmetic;
import com.wangjialun.ai.practice.kit.CityWeather;
import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step15 · Loop 运行时。循环外面加输入守卫、步数保险丝和输出守卫。
 */
public interface LoopAgent extends Agent {

    int MAX_STEPS = 4;
    int MAX_INPUT_CHARS = 500;

    class Impl implements LoopAgent {

        private static final List<String> BLOCKED = List.of("密码", "攻击", "hack", "漏洞", "暴力", "炸药");
        private static final String PROTOCOL = """
                你运行在受控循环里。每轮只输出 JSON：
                {"thought":"思考","action":"get_weather|calculator","action_input":"参数"}
                或 {"thought":"思考","final":"最终答案"}。
                """;

        private final ChatModel model;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step15 · Agent Loop 运行时（守卫 + 保险丝）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            String blocked = blockedWord(input);
            if (input.length() > MAX_INPUT_CHARS) {
                trace.add("guard", "输入守卫拦截", "输入超过 " + MAX_INPUT_CHARS + " 字符上限");
                return "输入过长，已拦截。";
            }
            if (blocked != null) {
                trace.add("guard", "输入守卫拦截", "命中敏感词: " + blocked);
                return "该请求涉及受限内容，已被输入守卫拦截。";
            }
            trace.add("guard", "输入守卫通过", "长度 " + input.length() + " 字符，未命中敏感词");

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(PROTOCOL));
            messages.add(Message.user(input));
            for (int step = 1; step <= MAX_STEPS; step++) {
                String raw;
                try {
                    raw = model.chat(messages).trim();
                } catch (RuntimeException ex) {
                    trace.add("warn", "异常兜底", "模型调用失败: " + ex.getMessage());
                    return "运行时异常已兜底，请稍后重试。";
                }
                Object parsed = Texts.parseLenient(raw);
                String thought = Json.str(parsed, "thought");
                if (thought != null) {
                    trace.add("thought", "Thought " + step, thought);
                }
                String fin = Json.str(parsed, "final");
                if (fin != null) {
                    if (blockedWord(fin) != null) {
                        trace.add("guard", "输出守卫拦截", "回答命中敏感词，已替换为安全话术");
                        return "回答未通过输出合规检查。";
                    }
                    trace.add("final", "输出守卫通过 · Final", "循环共执行 " + step + " 步");
                    return fin;
                }
                String action = Json.str(parsed, "action");
                String actionInput = Json.str(parsed, "action_input");
                trace.add("action", "Action " + step, action + "(" + actionInput + ")");
                String observation = run(action, actionInput);
                trace.add("observation", "Observation " + step, observation);
                messages.add(Message.assistant(raw));
                messages.add(Message.user("Observation: " + observation));
            }
            trace.add("guard", "保险丝熔断", "达到最大步数 " + MAX_STEPS);
            return "已达到最大执行步数（" + MAX_STEPS + "），强制终止。";
        }

        private static String blockedWord(String text) {
            String lower = text == null ? "" : text.toLowerCase();
            for (String word : BLOCKED) {
                if (lower.contains(word)) {
                    return word;
                }
            }
            return null;
        }

        private static String run(String action, String input) {
            return switch (action == null ? "" : action) {
                case "get_weather" -> CityWeather.query(input);
                case "calculator" -> Arithmetic.evalExact(input);
                default -> "未知工具: " + action;
            };
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
