package com.wangjialun.ai.toyagentscope.agent.step24;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import com.wangjialun.ai.toyagentscope.agent.support.ToolCalls;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step24 · Hooks 钩子 —— before 拦截 / after 脱敏 / 审计，挂钩即生效、摘钩即消失。
 * <p>
 * 框架版思路：在 AgentScope 的工具 SPI（{@link AgentTool}）上做<b>装饰器</b>——
 * {@link HookedTool} 包住真实工具，调用前跑 before 钩子（可拦截），调用后跑 after 钩子（可改写结果）。
 * 包装后的工具注册进 Toolkit，模型调用、代码直接调用都会经过钩子。
 * <p>
 * 为什么不用 Middleware？AgentScope 2.0 的 {@code MiddlewareBase.onActing} 能看到/改写工具调用参数，
 * 但工具结果由框架内部收集后写回上下文，中间件改写事件流并不会改变模型看到的结果；
 * 「按工具拦截 + 改写结果」用工具装饰器最直接（旧版 Hook API 已标记弃用）。
 * <p>
 * 三个钩子：审计（before，全部工具）· 风控（before，send_sms 含「密码/验证码」拦截）· 脱敏（after，手机号/邮箱打码）。
 * 命令：「查询：张三」「发送：给 138… 发 …」「钩子：开/关」「审计日志」。
 */
public interface HooksAgent extends Agent {

    enum Phase { BEFORE, AFTER }

    /** 一个钩子：before 返回非 null 表示拦截原因；after 返回改写后的结果。 */
    record Hook(String name, Phase phase, Predicate<String> match,
                Function<Map<String, Object>, String> before, Function<String, String> after) {
    }

    /** 模拟业务工具。 */
    class BizTools {
        @Tool(name = "query_user", description = "按姓名查询用户资料", readOnly = true)
        public String queryUser(@ToolParam(name = "name", description = "姓名") String name) {
            return name + "，手机 13812345678，邮箱 " + "zhangsan@example.com" + "，会员等级 VIP2";
        }

        @Tool(name = "send_sms", description = "给手机号发送短信")
        public String sendSms(@ToolParam(name = "phone", description = "手机号") String phone,
                              @ToolParam(name = "content", description = "短信内容") String content) {
            return "短信已发送至 " + phone + "：" + content + "（模拟）";
        }
    }

    /** 工具装饰器：在真实工具外面套一层钩子链。 */
    class HookedTool implements AgentTool {

        private final AgentTool delegate;
        private final Impl host;

        HookedTool(AgentTool delegate, Impl host) {
            this.delegate = delegate;
            this.host = host;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public Map<String, Object> getParameters() {
            return delegate.getParameters();
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            List<Hook> hooks = host.activeHooks();
            for (Hook h : hooks) {
                if (h.phase() == Phase.BEFORE && h.match().test(getName())) {
                    String block = h.before().apply(param.getInput());
                    if (block != null) {
                        host.log.add("guard", "before · " + h.name() + " 拦截", block);
                        return Mono.just(ToolResultBlock.error("被钩子「" + h.name() + "」拦截：" + block));
                    }
                }
            }
            return delegate.callAsync(param).map(result -> {
                String text = Texts.textOf(result);
                for (Hook h : hooks) {
                    if (h.phase() == Phase.AFTER && h.match().test(getName())) {
                        String changed = h.after().apply(text);
                        if (!changed.equals(text)) host.log.add("thought", "after · " + h.name(), text + " → " + changed);
                        text = changed;
                    }
                }
                return ToolResultBlock.text(text);
            });
        }
    }

    class Impl implements HooksAgent {

        private static final Pattern PHONE = Pattern.compile("(1\\d{2})\\d{4}(\\d{4})");
        private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+)");

        final TraceLog log = new TraceLog();
        private final List<String> audit = new ArrayList<>();
        private final Toolkit toolkit = new Toolkit();
        private final ReActAgent agent;
        private final List<Hook> hooks;
        private volatile boolean enabled = true;

        public Impl(Model model) {
            this.hooks = List.of(
                    new Hook("审计", Phase.BEFORE, t -> true, args -> {
                        audit.add(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " 调用 " + args);
                        log.add("thought", "before · 审计", "已记录调用参数 " + args);
                        return null;
                    }, null),
                    new Hook("风控-敏感外发", Phase.BEFORE, "send_sms"::equals, args -> {
                        String content = String.valueOf(args.get("content"));
                        return content.contains("密码") || content.contains("验证码") ? "短信内容含敏感信息（密码/验证码），禁止外发" : null;
                    }, null),
                    new Hook("脱敏", Phase.AFTER, t -> true, null,
                            text -> EMAIL.matcher(PHONE.matcher(text).replaceAll("$1****$2")).replaceAll("$1***$2")));

            // 真实工具先注册到一个内部 Toolkit，再逐个套上钩子装饰器注册到对外 Toolkit
            Toolkit raw = new Toolkit();
            raw.registerTool(new BizTools());
            for (String name : raw.getToolNames()) {
                toolkit.registerAgentTool(new HookedTool(raw.getTool(name), this));
            }
            this.agent = ReActAgent.builder()
                    .name("hooks")
                    .sysPrompt("你是客服助手，可以查询用户资料（query_user）和发送短信（send_sms）。工具返回什么就如实转述。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(3)
                    .build();
        }

        List<Hook> activeHooks() {
            return enabled ? hooks : List.of();
        }

        @Override
        public String name() {
            return "Step24 · Hooks 钩子（拦截 / 脱敏 / 审计）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            String text = input.trim();
            if (text.startsWith("钩子：") || text.startsWith("钩子:")) {
                enabled = text.contains("开");
                log.add("action", enabled ? "挂钩" : "摘钩", enabled ? "3 个钩子已生效：审计 / 风控 / 脱敏" : "全部钩子已摘除，工具裸奔");
                return enabled ? "🪝 钩子已挂上（审计 / 风控 / 脱敏）" : "钩子已全部摘除";
            }
            if (text.contains("审计日志")) {
                String all = audit.isEmpty() ? "（暂无审计记录）" : String.join("\n", audit);
                log.add("observation", "审计日志", all);
                return "审计日志：\n" + all;
            }
            if (text.startsWith("查询：") || text.startsWith("查询:")) {
                return direct("query_user", Map.of("name", text.substring(3).trim()));
            }
            if (text.startsWith("发送：") || text.startsWith("发送:")) {
                Matcher m = Pattern.compile("(1\\d{10})").matcher(text);
                String phone = m.find() ? m.group(1) : "未知号码";
                String content = text.substring(3).replace("给", "").replace(phone, "").replaceFirst("^\\s*发\\s*", "").trim();
                return direct("send_sms", Map.of("phone", phone, "content", content));
            }
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            return Tracer.of(log).run(agent.streamEvents(List.of(new UserMessage(input)), ctx)).text();
        }

        /** 确定性命令：代码直接调用（同样经过钩子装饰器）。 */
        private String direct(String tool, Map<String, Object> args) {
            log.add("action", "调用 " + tool, args.toString() + (enabled ? "" : "（钩子已摘除）"));
            String out = ToolCalls.callText(toolkit, tool, args);
            log.add("observation", "工具结果", out);
            log.add("final", "Final Answer", out);
            return out;
        }

        @Override
        public void reset() {
            audit.clear();
            enabled = true;
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
