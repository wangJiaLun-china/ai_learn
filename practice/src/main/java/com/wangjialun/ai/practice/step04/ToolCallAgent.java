package com.wangjialun.ai.practice.step04;

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
 * Step04 · Function Calling。模型用 {"tool","arguments"} 声明调用，
 * 执行结果以「执行结果」回填后再生成回答。
 */
public interface ToolCallAgent extends Agent {

    class Impl implements ToolCallAgent {

        private static final String PROTOCOL = """
                你可以调用工具：
                - get_weather(city): 查询天气
                - calculator(expression): 四则运算
                需要工具时只输出 {"tool":"工具名","arguments":{"参数名":"值"}}。
                不需要工具时直接用自然语言回答。
                """;

        private final ChatModel model;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step04 · 工具调用（Function Calling）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(PROTOCOL));
            messages.add(Message.user(input));

            String first = model.chat(messages).trim();
            Object decision = Texts.parseLenient(first);
            if (decision instanceof Map<?, ?> call && Json.str(call, "tool") != null) {
                String tool = Json.str(call, "tool");
                String args = Json.write(call.get("arguments"));
                trace.add("tool", "模型发起工具调用", tool + " " + args);
                String result = execute(tool, args);
                trace.add("observation", "工具返回结果", result);
                messages.add(Message.assistant(first));
                messages.add(Message.user("工具 " + tool + " 的执行结果: " + result));
                String answer = model.chat(messages);
                trace.add("final", "基于工具结果生成回答", "模型拿到观察结果后组织最终回答");
                return answer;
            }
            trace.add("final", "无需工具，直接回答", "模型判断自身知识足够");
            return first;
        }

        private String execute(String tool, String argsJson) {
            Object args = Texts.parseLenient(argsJson == null ? "{}" : argsJson);
            return switch (tool) {
                case "get_weather" -> CityWeather.query(Json.str(args, "city"));
                case "calculator" -> Arithmetic.evalExact(String.valueOf(Json.str(args, "expression")));
                default -> "未知工具: " + tool;
            };
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
