package com.wangjialun.ai.toyagentscope.agent.step18;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Step18 · 全流程智能体 —— 守卫 + 记忆 + ReAct + 工具 + 保险丝，串成一条生产级链路。
 * <pre>
 *  输入守卫 → 画像抽取 → [HarnessAgent: 系统提示词注入画像 → ReAct(工具) → 压缩记忆] → 输出守卫 → 记忆回写
 * </pre>
 * 每一环对应前面的某个场景，这里把它们装配到同一个 AgentScope 智能体上：
 * <ul>
 *   <li>守卫（Step15）：Java 层输入/输出检查；</li>
 *   <li>用户画像：正则抽取「我叫…」「我喜欢…」，通过 Middleware 的 onSystemPrompt 动态注入系统提示词；</li>
 *   <li>记忆（Step05）：同一 sessionId 跨回合 + HarnessAgent compaction 自动压缩；</li>
 *   <li>ReAct + 工具（Step03）、保险丝 maxIters（Step15）。</li>
 * </ul>
 */
public interface FullAgent extends Agent {

    int MAX_INPUT = 300;
    int MAX_STEPS = 5;
    Pattern NAME_PTN = Pattern.compile("我叫([\\p{IsHan}A-Za-z0-9]{1,10}?)(?:[，,。！!；;\\s]|$)");
    Pattern LIKE_PTN = Pattern.compile("我(?:最)?喜欢([^，,。！!；;\\s]{1,12})");

    class Impl implements FullAgent, AutoCloseable {

        private final HarnessAgent agent;
        private final TraceLog log = new TraceLog();
        private final Map<String, String> profile = new LinkedHashMap<>();
        private RuntimeContext ctx = newCtx();

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new CommonTools());
            this.agent = HarnessAgent.builder()
                    .name("full")
                    .agentId("step18")
                    .sysPrompt("你是一个全能助手：会记住用户信息，需要天气/计算/时间时调用工具，回答简洁。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(MAX_STEPS)
                    .middleware(new ProfileMiddleware())
                    .workspace(Path.of("data", "workspace", "step18"))
                    // 会话状态只放内存：HarnessAgent 默认会写到 ~/.agentscope/state/（disableSessionPersistence 管不到它）
                    .stateStore(new InMemoryAgentStateStore())
                    .compaction(CompactionConfig.builder().triggerMessages(12).keepMessages(6)
                            .flushBeforeCompact(false).offloadBeforeCompact(false).build())
                    .disableFilesystemTools().disableShellTool().disableWebTools()
                    .disableMemoryTools().disableMemoryHooks()
                    .disableSubagents().disableToolsConfig().disableToolResultEviction()
                    .disableSessionPersistence().disableTranscript()
                    .disableWorkspaceContext().skillsEnabled(false)
                    .build();
        }

        /** 画像注入：每次推理前把用户画像追加到系统提示词里。 */
        private class ProfileMiddleware implements MiddlewareBase {
            @Override
            public Mono<String> onSystemPrompt(io.agentscope.core.agent.Agent a, RuntimeContext c, String current) {
                if (profile.isEmpty()) return Mono.just(current);
                return Mono.just(current + "\n\n【用户画像】" + profileText());
            }
        }

        @Override
        public String name() {
            return "Step18 · 全流程智能体（守卫+记忆+ReAct+工具+保险丝）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            // 1. 输入守卫
            if (input.length() > MAX_INPUT) {
                log.add("guard", "输入守卫", "输入过长：" + input.length() + " 字符，超过 " + MAX_INPUT + " 上限，已拦截");
                return "输入过长，请精简到 " + MAX_INPUT + " 字以内。";
            }
            String hit = Texts.sensitiveHit(input);
            if (hit != null) {
                log.add("guard", "输入守卫", "命中敏感词「" + hit + "」，已拦截并拒绝");
                return "抱歉，这个请求涉及不安全的内容，我无法协助。";
            }
            log.add("guard", "输入守卫", "通过：长度 " + input.length() + "，未命中敏感词");

            // 2. 画像抽取 + 读取记忆
            extractProfile(input);
            int ctxSize = agent.getDelegate().getAgentState(ctx).getContext().size();
            log.add("memory", "读取记忆", "会话上下文 " + ctxSize + " 条；画像：" + (profile.isEmpty() ? "（空）" : profileText()));

            // 3. ReAct + 工具 + 保险丝
            Tracer.Result r = Tracer.of(log)
                    .noFinal()
                    .run(agent.streamEvents(List.of(new UserMessage(input)), ctx));
            String answer = r.text();
            if (r.is(GenerateReason.MAX_ITERATIONS)) {
                log.add("guard", "保险丝", "达到最大步数 " + MAX_STEPS + "，熔断");
            }
            log.add("final", "Final Answer", answer);

            // 4. 输出守卫
            String outHit = Texts.sensitiveHit(answer);
            if (outHit != null) {
                log.add("guard", "输出守卫", "回答命中敏感词「" + outHit + "」，已拦截替换");
                answer = "抱歉，这个回答涉及不安全的内容，已被拦截。";
            } else {
                log.add("guard", "输出守卫", "通过");
            }

            // 5. 记忆回写
            log.add("memory", "回写记忆", "本轮已写入会话上下文（共 " + agent.getDelegate().getAgentState(ctx).getContext().size()
                    + " 条，超过 12 条自动压缩）；画像：" + (profile.isEmpty() ? "（空）" : profileText()));
            return answer;
        }

        private void extractProfile(String input) {
            Matcher n = NAME_PTN.matcher(input);
            if (n.find()) profile.put("姓名", n.group(1));
            Matcher l = LIKE_PTN.matcher(input);
            if (l.find()) profile.put("喜好", l.group(1));
        }

        private String profileText() {
            return profile.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("，"));
        }

        private static RuntimeContext newCtx() {
            return RuntimeContext.builder().userId("toy").sessionId("step18-" + UUID.randomUUID()).build();
        }

        @Override
        public void reset() {
            agent.clearContext(ctx);
            ctx = newCtx();
            profile.clear();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }

        @Override
        public void close() {
            agent.close();
        }
    }
}
