package com.wangjialun.ai.toyagentscope.agent.step17;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Step17 · ReAct 运行时 —— turn / step 两级循环 + 上下文裁剪 + TurnEndReason。
 * <p>
 * <ul>
 *   <li><b>turn（回合）</b>：用户每说一句话是一个回合；<b>step（步）</b>：回合内每次「调模型 → 执行工具」是一步。
 *       同一个 sessionId 跨回合保留历史，{@code maxIters(6)} 限制每回合步数；</li>
 *   <li><b>上下文裁剪</b>：{@link ContextTrimMiddleware} 是一个 Middleware，挂在 onModelCall 上 ——
 *       每次调模型前按 token 预算从最旧的消息开始裁掉，<b>只影响本次发给模型的视图</b>，会话状态里的完整历史不变；</li>
 *   <li><b>TurnEndReason</b>：把框架的 GenerateReason 映射成封闭类型（见 {@link TurnEndReason}）。</li>
 * </ul>
 * 「死循环测试」：check_job 永远返回 RUNNING，模型会一直轮询，直到第 6 步被保险丝熔断。
 */
public interface RuntimeAgent extends Agent {

    int MAX_STEPS_PER_TURN = 6;
    int BUDGET_TOKENS = 400;
    int KEEP_MIN = 4;

    class RuntimeTools {

        @Tool(name = "echo", description = "原样返回输入文本", readOnly = true)
        public String echo(@ToolParam(name = "text", description = "任意文本") String text) {
            return text;
        }

        @Tool(name = "check_job", description = "查询后台任务状态，返回 RUNNING 或 DONE", readOnly = true)
        public String checkJob(@ToolParam(name = "job_id", description = "任务 ID") String jobId) {
            return "RUNNING（任务 " + jobId + " 仍在处理中，请继续查询）";
        }
    }

    /** 上下文裁剪中间件：在每次模型调用前，把消息裁剪到 token 预算以内。 */
    class ContextTrimMiddleware implements MiddlewareBase {

        @Override
        public Flux<AgentEvent> onModelCall(io.agentscope.core.agent.Agent agent, RuntimeContext ctx, ModelCallInput input,
                                            Function<ModelCallInput, Flux<AgentEvent>> next) {
            List<Msg> msgs = input.messages();
            int before = estimate(msgs);
            if (before <= BUDGET_TOKENS) return next.apply(input);

            List<Msg> system = msgs.stream().filter(m -> m.getRole() == MsgRole.SYSTEM).toList();
            List<Msg> rest = new ArrayList<>(msgs.stream().filter(m -> m.getRole() != MsgRole.SYSTEM).toList());
            int dropped = 0;
            while (rest.size() > KEEP_MIN && estimate(system) + estimate(rest) > BUDGET_TOKENS) {
                rest.remove(0);
                dropped++;
            }
            // 裁剪后必须从一条「真正的用户消息」开始，避免留下孤立的工具结果
            while (rest.size() > 1 && (rest.get(0).getRole() != MsgRole.USER || rest.get(0).hasContentBlocks(ToolResultBlock.class))) {
                rest.remove(0);
                dropped++;
            }
            List<Msg> trimmed = new ArrayList<>(system);
            trimmed.addAll(rest);
            TraceLog log = ctx.get(TraceLog.class);
            if (log != null) {
                log.add("compress", "上下文裁剪 ContextTrimMiddleware",
                        "估算 " + before + " tokens > 预算 " + BUDGET_TOKENS + "，丢弃最旧 " + dropped + " 条，剩 "
                                + trimmed.size() + " 条（约 " + estimate(trimmed) + " tokens）");
            }
            return next.apply(new ModelCallInput(trimmed, input.tools(), input.options(), input.model()));
        }

        /** 粗略 token 估算：中文约 1 字 ≈ 1 token 的一半多，这里取 字符数 / 2。 */
        static int estimate(List<Msg> msgs) {
            return msgs.stream().mapToInt(m -> {
                String t = m.getTextContent();
                return (t == null ? 0 : t.length()) / 2 + 4;
            }).sum();
        }
    }

    class Impl implements RuntimeAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();
        private final String sessionId = "step17-" + UUID.randomUUID();
        private int turn;

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new CommonTools());
            toolkit.registerTool(new RuntimeTools());
            this.agent = ReActAgent.builder()
                    .name("runtime")
                    .sysPrompt("你是一个会使用工具的助手。当用户说「死循环测试」时，调用 check_job 查询任务 job-1，必须等到返回 DONE 才能回答。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(MAX_STEPS_PER_TURN)
                    .middleware(new ContextTrimMiddleware())
                    .build();
        }

        @Override
        public String name() {
            return "Step17 · ReAct 运行时（turn/step 两级循环 + 上下文裁剪）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            turn++;
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(sessionId)
                    .put(TraceLog.class, log).build();
            Tracer.Result r = Tracer.of(log).noFinal().run(agent.streamEvents(List.of(new UserMessage(input)), ctx));
            TurnEndReason reason = TurnEndReason.from(r.reason(), r.text(), MAX_STEPS_PER_TURN);
            String label = "Turn " + turn + " 结束 · " + reason.getClass().getSimpleName();
            // Java 17：用 instanceof 模式匹配逐一处理封闭类型的三种结局
            if (reason instanceof TurnEndReason.Completed c) {
                log.add("final", label, c.summary());
                return c.summary();
            }
            if (reason instanceof TurnEndReason.MaxSteps s) {
                log.add("guard", label, "单回合达到 " + s.steps() + " 步上限，保险丝熔断");
                return "本回合已执行 " + s.steps() + " 步仍未完成，已强制停止（TurnEndReason.MaxSteps）。"
                        + (r.text().isBlank() ? "" : "\n" + r.text());
            }
            TurnEndReason.Error e = (TurnEndReason.Error) reason;
            log.add("warn", label, e.message());
            return "回合异常结束：" + e.message();
        }

        @Override
        public void reset() {
            agent.clearContext("toy", sessionId);
            turn = 0;
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
