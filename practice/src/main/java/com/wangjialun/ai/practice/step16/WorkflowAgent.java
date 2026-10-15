package com.wangjialun.ai.practice.step16;

import com.wangjialun.ai.practice.kit.Arithmetic;
import com.wangjialun.ai.practice.kit.CityWeather;
import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step16 · 工作流。节点读写同一份状态，条件边决定下一步，最后都汇合到润色节点。
 */
public interface WorkflowAgent extends Agent {

    class Impl implements WorkflowAgent {

        private final ChatModel model;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step16 · 工作流状态机（LangGraph 思想）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("input", input);
            state.put("answer", "");
            state.put("observation", "");
            run("classify", state);
            String next = String.valueOf(state.getOrDefault("next", "faq"));
            trace.add("edge", "条件边", "classify --" + next + "--> " + ("tool".equals(next) ? "tool 节点" : "faq 节点"));
            run(next, state);
            run("polish", state);
            trace.add("edge", "END", "执行路径: classify → " + next + " → polish → END");
            return String.valueOf(state.get("answer"));
        }

        private void run(String node, Map<String, Object> state) {
            String input = String.valueOf(state.get("input"));
            String marker = "[节点:" + node + "] ";
            switch (node) {
                case "classify" -> {
                    Object parsed = Texts.parseLenient(model.chat(List.of(
                            Message.system(marker + "你是意图分类器，只输出 {\"next\":\"faq|tool\",\"reason\":\"一句话\"}。天气或四则运算走 tool，其他走 faq。"),
                            Message.user(input))));
                    String next = Json.str(parsed, "next");
                    state.put("next", next == null ? "faq" : next);
                    trace.add("node", "节点 classify", "路由决策 → " + state.get("next"));
                }
                case "tool" -> {
                    Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)").matcher(input);
                    String observation = matcher.find()
                            ? "计算结果 " + Arithmetic.evalFirst(input)
                            : "「" + CityWeather.cityIn(input) + "」" + CityWeather.query(CityWeather.cityIn(input));
                    state.put("observation", observation);
                    trace.add("node", "节点 tool", observation);
                }
                case "faq" -> {
                    String answer = model.chat(List.of(
                            Message.system(marker + "你是 FAQ 问答节点，直接简洁回答常见问题。"),
                            Message.user(input)));
                    state.put("answer", answer);
                    trace.add("node", "节点 faq", Texts.clip(answer, 40));
                }
                case "polish" -> {
                    String observation = String.valueOf(state.get("observation"));
                    String base = observation.isEmpty() ? String.valueOf(state.get("answer")) : "根据查询结果：" + observation;
                    String answer = model.chat(List.of(
                            Message.system(marker + "你是润色节点。保持事实不变，整理最终回答。\n前序产出: " + base),
                            Message.user(input)));
                    state.put("answer", answer);
                    trace.add("node", "节点 polish", "最终回答已生成");
                }
                default -> trace.add("warn", "未知节点", node);
            }
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
