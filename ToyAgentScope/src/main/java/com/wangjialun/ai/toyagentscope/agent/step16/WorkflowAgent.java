package com.wangjialun.ai.toyagentscope.agent.step16;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import com.wangjialun.ai.toyagentscope.agent.support.ToolCalls;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step16 · 工作流状态机 —— LangGraph 思想：节点 + 条件边 + 共享状态。
 * <p>
 * 智能体（Step03）让模型决定下一步；工作流则由<b>开发者</b>画好图，模型只在节点内干活：
 * <pre>
 *   classify ──tool──▶ tool ──┐
 *       └─────faq────▶ faq  ──┴─▶ polish ─▶ END
 * </pre>
 * 框架版：AgentScope 2.0 没有内置图引擎，图就是普通 Java 代码（显式状态机）；
 * 每个节点用的是框架积木 ——
 * classify 用结构化输出（{@link Route}），tool 节点直接 {@code toolkit.callTool(...)}，
 * faq / polish 节点各是一个带专属提示词的 ReActAgent。共享状态是一个 Map。
 */
public interface WorkflowAgent extends Agent {

    record Route(String next, String toolName, String argument) {
    }

    class Impl implements WorkflowAgent {

        private final ReActAgent classifier;
        private final ReActAgent faq;
        private final ReActAgent polisher;
        private final Toolkit toolkit = new Toolkit();
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            toolkit.registerTool(new CommonTools());
            this.classifier = ReActAgent.builder().name("classify").model(model).sysPrompt("""
                    你是工作流的 classify 节点。判断用户问题走哪条边：
                    - 需要查天气或做计算 → next=tool，toolName 取 get_weather 或 calculator，argument 填城市名或算式；
                    - 其他知识问答 → next=faq，toolName 与 argument 留空。
                    """).build();
            this.faq = ReActAgent.builder().name("faq").model(model)
                    .sysPrompt("你是工作流的 faq 节点，用 2-3 句话准确回答 AI 智能体相关的知识问题。").build();
            this.polisher = ReActAgent.builder().name("polish").model(model)
                    .sysPrompt("你是工作流的 polish 节点，把给定的素材润色成一句自然、友好的中文回答，不要添加新事实。").build();
        }

        @Override
        public String name() {
            return "Step16 · 工作流状态机（节点 + 条件边）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            Map<String, Object> state = new HashMap<>();
            state.put("input", input);

            // 节点 classify（结构化输出）
            Msg m = classifier.call(List.of(new UserMessage(input)), Route.class, ctx()).block();
            Route route = Texts.structured(m, Route.class);
            String next = route != null && "tool".equalsIgnoreCase(route.next()) ? "tool" : "faq";
            state.put("route", route);
            log.add("node", "🔹 节点 classify", "路由决策 → " + next + ("tool".equals(next) ? "（" + route.toolName() + " " + route.argument() + "）" : ""));
            log.add("edge", "条件边", "classify --" + next + "--> " + next + " 节点");

            // 节点 tool / faq
            if ("tool".equals(next)) {
                String toolName = "calculator".equals(route.toolName()) ? "calculator" : "get_weather";
                Map<String, Object> args = "calculator".equals(toolName)
                        ? Map.of("expression", String.valueOf(route.argument()))
                        : Map.of("city", String.valueOf(route.argument()));
                String obs = ToolCalls.callText(toolkit, toolName, args);
                state.put("material", toolName + "(" + route.argument() + ") = " + obs);
                log.add("node", "🔹 节点 tool", toolName + "(" + route.argument() + ") → " + obs);
            } else {
                String answer = text(faq, input);
                state.put("material", answer);
                log.add("node", "🔹 节点 faq", Texts.cut(answer, 80));
            }

            // 节点 polish（汇合点）
            String finalAnswer = text(polisher, "用户问题：" + input + "\n素材：" + state.get("material"));
            state.put("answer", finalAnswer);
            log.add("node", "🔹 节点 polish", "最终回答已生成");
            log.add("edge", "END", "执行路径: classify → " + next + " → polish → END");
            return finalAnswer;
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
