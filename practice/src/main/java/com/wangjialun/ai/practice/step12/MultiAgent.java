package com.wangjialun.ai.practice.step12;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.util.List;
import java.util.Map;

/**
 * Step12 · 多智能体。规划者拆任务，研究员找要点，写手成文，审查员把关。
 * 每个角色只靠不同的系统提示词区分，协作就是把上一位的产出传给下一位。
 */
public interface MultiAgent extends Agent {

    class Impl implements MultiAgent {

        private final ChatModel model;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step12 · 多智能体协作（规划-执行-审查）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            String planRaw = ask("你是规划者（Planner）。把任务拆成研究员、写手、审查员三步，只输出 JSON："
                    + "{\"plan\":[{\"role\":\"研究员\",\"task\":\"...\"},{\"role\":\"写手\",\"task\":\"...\"},{\"role\":\"审查员\",\"task\":\"...\"}]}", input);
            Object planObj = Texts.parseLenient(planRaw);
            List<?> plan = planObj instanceof Map<?, ?> map && map.get("plan") instanceof List<?> steps ? steps : List.of();
            trace.add("agent", "规划者", "产出 " + plan.size() + " 步计划：" + roles(plan));

            String notes = ask("你是研究员（Researcher），围绕任务输出 3 条以内要点，每条一句话。", input);
            trace.add("agent", "研究员", Texts.clip(notes, 60));

            String draft = ask("你是写手（Writer）。基于研究笔记写 100 字以内短文。\n研究笔记: " + notes, input);
            trace.add("agent", "写手", Texts.clip(draft, 60));

            Object review = Texts.parseLenient(ask("你是审查员（Reviewer）。只输出 {\"pass\":true,\"comment\":\"一句话点评\"}。", draft));
            boolean pass = review instanceof Map<?, ?> map && Boolean.parseBoolean(String.valueOf(map.get("pass")));
            String comment = Json.str(review, "comment");
            trace.add(pass ? "guard" : "warn", pass ? "审查员：通过" : "审查员：打回", comment == null ? "" : comment);

            String delivered = pass ? draft : ask("根据审查意见修改短文，直接输出修改后的版本。", "原文: " + draft + "\n审查意见: " + comment);
            trace.add("final", "主持者汇总", "协作链路：规划 → 研究 → 写作 → 审查 → 交付");
            return delivered;
        }

        private String ask(String system, String user) {
            return model.chat(List.of(Message.system(system), Message.user(user))).trim();
        }

        private static String roles(List<?> plan) {
            StringBuilder sb = new StringBuilder();
            for (Object item : plan) {
                if (item instanceof Map<?, ?> step) {
                    if (!sb.isEmpty()) {
                        sb.append("→");
                    }
                    sb.append(step.get("role"));
                }
            }
            return sb.toString();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
