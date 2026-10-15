package com.wangjialun.ai.toyagentscope.agent.step12;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step12 · 多智能体协作 —— 规划者 → 研究员 → 写作者 → 审查者。
 * <p>
 * 手写版：同一个模型换 4 套提示词，手动串起来。
 * <p>
 * 框架版：4 个独立的 {@link ReActAgent}（各自的 name / sysPrompt / 上下文），由 Java 代码顺序编排。
 * <ul>
 *   <li>规划者、审查者用<b>结构化输出</b>返回 Plan / Review 强类型对象；</li>
 *   <li>审查不通过 → 写作者带着审查意见重写一次（反馈回路）。</li>
 * </ul>
 * 说明：AgentScope 1.x 的 Pipeline / MsgHub 在 2.0 中已移除，官方建议用
 * 「普通 Java 编排 + 子代理（见 Step13）+ 事件流」组合多智能体 —— 正如本场景所做。
 */
public interface MultiAgent extends Agent {

    record Plan(List<String> steps) {
    }

    record Review(boolean pass, String comment) {
    }

    class Impl implements MultiAgent {

        private final ReActAgent planner;
        private final ReActAgent researcher;
        private final ReActAgent writer;
        private final ReActAgent reviewer;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            this.planner = role(model, "planner", "你是规划者。把写作任务拆成 3 个简短步骤（每步不超过 15 字）。");
            this.researcher = role(model, "researcher", "你是研究员。根据任务与计划，列出 3-5 条关键事实要点作为研究笔记，每条一句话。");
            this.writer = role(model, "writer", "你是写作者。根据研究笔记写出满足任务要求的短文，语言通俗，直接输出正文。");
            this.reviewer = role(model, "reviewer", "你是审查者。检查短文是否满足任务要求（字数、主题、准确性）。满足则 pass=true；否则 pass=false 并在 comment 给出具体修改意见。");
        }

        private static ReActAgent role(Model model, String name, String prompt) {
            return ReActAgent.builder().name(name).sysPrompt(prompt).model(model).build();
        }

        @Override
        public String name() {
            return "Step12 · 多智能体协作（规划-执行-审查）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            // 1. 规划者：结构化输出 Plan
            Msg planMsg = planner.call(List.of(new UserMessage(input)), Plan.class, ctx()).block();
            Plan plan = Texts.structured(planMsg, Plan.class);
            String planText = plan == null || plan.steps() == null ? "（规划失败）" : String.join(" → ", plan.steps());
            log.add("agent", "Planner 规划者", planText);

            // 2. 研究员
            String notes = text(researcher, "任务：" + input + "\n计划：" + planText);
            log.add("agent", "Researcher 研究员", notes);

            // 3. 写作者
            String draft = text(writer, "任务：" + input + "\n研究笔记：\n" + notes);
            log.add("agent", "Writer 写作者", Texts.cut(draft, 300));

            // 4. 审查者：结构化输出 Review，不通过则带意见重写一次
            Msg reviewMsg = reviewer.call(List.of(new UserMessage("任务：" + input + "\n短文：\n" + draft)), Review.class, ctx()).block();
            Review review = Texts.structured(reviewMsg, Review.class);
            if (review == null || review.pass()) {
                log.add("guard", "Reviewer 审查通过", review == null ? "（审查结果解析失败，默认通过）" : "pass=true " + (review.comment() == null ? "" : review.comment()));
            } else {
                log.add("warn", "Reviewer 审查不通过 → 重写", review.comment());
                draft = text(writer, "任务：" + input + "\n研究笔记：\n" + notes + "\n上一版：\n" + draft + "\n审查意见：" + review.comment() + "\n请按意见重写。");
            }
            log.add("final", "Final Answer", draft);
            return draft;
        }

        private static String text(ReActAgent agent, String prompt) {
            Msg m = agent.call(List.of(new UserMessage(prompt)), ctx()).block();
            return m == null ? "" : m.getTextContent();
        }

        private static RuntimeContext ctx() {
            return RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
