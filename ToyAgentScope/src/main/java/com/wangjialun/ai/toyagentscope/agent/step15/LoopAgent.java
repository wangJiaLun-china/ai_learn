package com.wangjialun.ai.toyagentscope.agent.step15;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step15 · Loop 运行时 —— 守卫 + 保险丝 + 兜底。
 * <p>
 * 把智能体放进生产环境，循环外面必须包三层防护：
 * <ul>
 *   <li><b>守卫（Guard）</b>：输入进来前、输出出去前各检查一次（长度、敏感词）——业务规则，写在 Java 层；</li>
 *   <li><b>保险丝（Fuse）</b>：{@code maxIters(4)}，ReAct 循环超过 4 轮由框架强制熔断（EXCEED_MAX_ITERS）；</li>
 *   <li><b>兜底（Fallback）</b>：{@code maxRetries(2)} 模型调用失败自动重试，{@code fallbackModel(...)}
 *       主模型持续失败时切换备用模型；再失败则 catch 住，返回安全话术而不是把异常抛给用户。</li>
 * </ul>
 */
public interface LoopAgent extends Agent {

    int MAX_INPUT_CHARS = 200;
    int MAX_STEPS = 4;
    String SAFE_REPLY = "抱歉，这个请求涉及不安全的内容，我无法协助。";

    class Impl implements LoopAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new CommonTools());
            this.agent = ReActAgent.builder()
                    .name("loop")
                    .sysPrompt("你是一个会使用工具的助手，需要实时信息或计算时先调用工具。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(MAX_STEPS)        // 保险丝
                    .maxRetries(2)              // 兜底：失败重试
                    .fallbackModel(model)       // 兜底：备用模型（教学中用同一个，生产换成另一家）
                    .build();
        }

        @Override
        public String name() {
            return "Step15 · Loop 运行时（守卫 + 保险丝 + 兜底）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            // 1. 输入守卫
            if (input.length() > MAX_INPUT_CHARS) {
                log.add("guard", "⛔ 输入守卫拦截", "输入 " + input.length() + " 字符，超过 " + MAX_INPUT_CHARS + " 字符上限");
                return "输入过长（超过 " + MAX_INPUT_CHARS + " 字符），请精简后再试。";
            }
            String hit = Texts.sensitiveHit(input);
            if (hit != null) {
                log.add("guard", "⛔ 输入守卫拦截", "命中敏感词: " + hit + "（Prompt 注入/安全防护）");
                return SAFE_REPLY;
            }
            log.add("guard", "🛡️ 输入守卫通过", "长度 " + input.length() + " 字符，未命中敏感词");

            // 2. 框架循环（保险丝 + 重试 + 备用模型都在框架里）
            Tracer.Result r;
            try {
                RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
                r = Tracer.of(log).noFinal().run(agent.streamEvents(List.of(new UserMessage(input)), ctx));
            } catch (Exception e) {
                log.add("warn", "⚠️ 异常兜底", "模型调用失败（已重试 + 备用模型）: " + e.getMessage());
                return "服务暂时不可用，请稍后再试。";
            }
            if (r.is(GenerateReason.MAX_ITERATIONS)) {
                log.add("guard", "🔥 保险丝熔断", "达到最大步数 " + MAX_STEPS + "，强制终止循环");
            }

            // 3. 输出守卫
            String out = Texts.sensitiveHit(r.text());
            if (out != null) {
                log.add("guard", "⛔ 输出守卫拦截", "回答命中敏感词「" + out + "」，已替换为安全话术");
                return SAFE_REPLY;
            }
            log.add("final", "✅ 输出守卫通过 · Final", r.text());
            return r.text();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
