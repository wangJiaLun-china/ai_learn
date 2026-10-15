package com.wangjialun.ai.practice.step06;

import com.wangjialun.ai.practice.kit.Arithmetic;
import com.wangjialun.ai.practice.kit.CityWeather;
import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.util.List;
import java.util.Map;

/**
 * Step06 · 意图路由。先让模型把输入分成 weather / math / chat，再走对应处理器。
 */
public interface RouterAgent extends Agent {

    class Impl implements RouterAgent {

        private static final String ROUTER = """
                你是意图路由器。只输出 JSON：{"intent":"weather|math|chat","reason":"一句话理由"}
                weather 表示查天气，math 表示四则运算，其余都是 chat。
                """;

        private final ChatModel model;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step06 · 意图识别与决策中枢（路由）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            Object decision = Texts.parseLenient(model.chat(List.of(Message.system(ROUTER), Message.user(input))));
            String intent = Json.str(decision, "intent");
            if (intent == null) {
                intent = "chat";
            }
            String reason = Json.str(decision, "reason");
            trace.add("intent", "意图识别", intent + "（" + (reason == null ? "" : reason) + "）");
            return switch (intent) {
                case "weather" -> weather(input);
                case "math" -> math(input);
                default -> talk(input);
            };
        }

        private String weather(String input) {
            String city = CityWeather.cityIn(input);
            trace.add("action", "路由 → 天气工具", "get_weather(" + city + ")");
            String result = CityWeather.query(city);
            trace.add("observation", "工具返回", result);
            return "「" + city + "」现在" + result + "。";
        }

        private String math(String input) {
            trace.add("action", "路由 → 计算器", input);
            return "计算结果：" + Arithmetic.evalFirst(input) + "。";
        }

        private String talk(String input) {
            trace.add("action", "路由 → 对话模型", "无工具直答");
            return model.chat(List.of(
                    Message.system("你是一个友好的中文聊天助手。"),
                    Message.user(input)));
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
