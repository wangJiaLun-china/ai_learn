package com.wangjialun.ai.toyagentscope.agent.step19;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.Toolkit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step19 · 人工介入 —— ask_user_question：提问挂起 → 人工答复 → 续跑。
 * <p>
 * 手写版：模型输出 {"ask":"…"}，Java 把问题存进 pendingQuestion，下一条消息当作答案。
 * <p>
 * 框架版：<b>外部执行工具（external tool）</b>。
 * <ol>
 *   <li>{@code ask_user} 是一个 {@code externalTool(true)} 的 {@link ToolBase} —— 框架不在本地执行它；</li>
 *   <li>模型调用它时，ReActAgent 发出 REQUIRE_EXTERNAL_EXECUTION 事件并<b>挂起</b>，
 *       结束原因为 TOOL_SUSPENDED，会话状态里保留着这次未完成的工具调用；</li>
 *   <li>用户答复后，我们把答复包装成同一个 toolCallId 的工具结果（{@link ToolResultMessage}）
 *       作为下一次输入，框架校验 ID 后从断点继续推理。</li>
 * </ol>
 */
public interface AskAgent extends Agent {

    class Impl implements AskAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();
        private RuntimeContext ctx = newCtx();
        private ToolUseBlock pending;

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new CommonTools());
            toolkit.registerAgentTool(new AskUserTool());
            this.agent = ReActAgent.builder()
                    .name("ask")
                    .sysPrompt("""
                            你是一个出行助手。执行任务时如果缺少关键信息（如出发地、出发日期），
                            不要猜测，也不要在回复正文里直接提问 —— 必须调用 ask_user 工具提问，一次只问一个问题。
                            信息齐全后再给出结果。订票类任务只需模拟给出方案，无需真实下单。
                            """)
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(6)
                    .build();
        }

        /** 外部执行工具：框架只负责「把问题交出去并挂起」，答案由人类提供。 */
        static class AskUserTool extends ToolBase {
            AskUserTool() {
                super(ToolBase.builder()
                        .name("ask_user")
                        .description("向用户提一个澄清问题并等待人工答复（缺少关键信息时使用）")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of("question", Map.of("type", "string", "description", "要问用户的问题")),
                                "required", List.of("question")))
                        .externalTool(true));
            }
        }

        @Override
        public String name() {
            return "Step19 · 人工介入（ask_user_question 挂起续跑）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            List<Msg> msgs;
            if (pending != null) {
                // 续跑：把人工答复作为 ask_user 的工具结果交回框架
                log.add("observation", "人工答复（回填 ask_user 结果）", input);
                msgs = List.of(new ToolResultMessage(pending.getId(), pending.getName(), "人工答复：" + input));
                pending = null;
            } else {
                msgs = List.of(new UserMessage(input));
            }

            Tracer.Result r = Tracer.of(log)
                    .actionType(tool -> "action")
                    .run(agent.streamEvents(msgs, ctx));

            if (r.is(GenerateReason.TOOL_SUSPENDED) && r.msg() != null) {
                pending = r.msg().getContentBlocks(ToolUseBlock.class).stream()
                        .filter(t -> "ask_user".equals(t.getName()))
                        .reduce((a, b) -> b).orElse(null);
                String question = pending == null ? "（缺少信息）" : String.valueOf(pending.getInput().get("question"));
                return "🙋 需要你补充信息：" + question;
            }
            return r.text();
        }

        private static RuntimeContext newCtx() {
            return RuntimeContext.builder().userId("toy").sessionId("step19-" + UUID.randomUUID()).build();
        }

        @Override
        public void reset() {
            agent.clearContext(ctx);
            ctx = newCtx();
            pending = null;
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
