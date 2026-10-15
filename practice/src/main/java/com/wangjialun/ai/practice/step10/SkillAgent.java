package com.wangjialun.ai.practice.step10;

import com.wangjialun.ai.practice.kit.CityWeather;
import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Step10 · Skills。模型先选技能名，智能体再按 L0 提示词或 L1 工具组合去执行。
 */
public interface SkillAgent extends Agent {

    class Impl implements SkillAgent {

        private static final String SELECT = """
                你是技能选择器。已注册技能：
                - travel_plan(L1): 旅行攻略，组合天气和时间
                - weather_report(L1): 天气播报
                - free_chat(L0): 自由聊天，不调用工具
                只输出 {"skill":"技能名","reason":"一句话理由"}。
                """;

        private final ChatModel model;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step10 · 技能编排（Skills L0/L1/L2）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            Object decision = Texts.parseLenient(model.chat(List.of(Message.system(SELECT), Message.user(input))));
            String skill = Json.str(decision, "skill");
            if (skill == null) {
                skill = "free_chat";
            }
            String reason = Json.str(decision, "reason");
            trace.add("skill", "技能选择", skill + "（" + (reason == null ? "" : reason) + "）");
            return switch (skill) {
                case "travel_plan" -> travelPlan(input);
                case "weather_report" -> weatherReport(input);
                default -> freeChat(input);
            };
        }

        /** L1：固定先查天气再取时间，把两份事实交给旅行顾问提示词。 */
        private String travelPlan(String input) {
            String city = CityWeather.cityIn(input);
            trace.add("action", "L1 组合工具 1/2", "get_weather(" + city + ")");
            String weather = CityWeather.query(city);
            trace.add("observation", "工具返回", weather);
            trace.add("action", "L1 组合工具 2/2", "get_time()");
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            trace.add("observation", "工具返回", time);
            String answer = model.chat(List.of(
                    Message.system("你是旅行顾问。基于天气与时间，用 3 条以内要点给出行建议。"),
                    Message.user(input + "\n天气: " + weather + "\n当前时间: " + time)));
            trace.add("final", "L1 技能完成", "两件工具的结果已拼进提示词");
            return answer;
        }

        private String weatherReport(String input) {
            String city = CityWeather.cityIn(input);
            trace.add("action", "L1 调用工具", "get_weather(" + city + ")");
            String weather = CityWeather.query(city);
            trace.add("final", "播报完成", weather);
            return "「" + city + "」" + weather + "。";
        }

        /** L0：只有提示词，不碰工具。 */
        private String freeChat(String input) {
            trace.add("final", "L0 提示词技能", "无工具，直接对话");
            return model.chat(List.of(Message.system("你是友好的中文助手，回答保持简洁。"), Message.user(input)));
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
