package com.wangjialun.ai.toyagentscope.agent.step20;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Step20 · 审批门禁 —— 风险分级 + 审批挂起 + 会话免审 + 超时默认 DENY。
 * <p>
 * 手写版：自己维护高风险工具清单、pendingApproval、trustedTools 集合。
 * <p>
 * 框架版：AgentScope 的<b>权限系统</b>（io.agentscope.core.permission）——
 * <ul>
 *   <li><b>风险分级</b>：PermissionContextState 里只给只读工具配 ALLOW 规则，其余（高风险）工具默认 ASK；</li>
 *   <li><b>审批挂起</b>：模型想调用 ASK 工具时，框架发出 REQUIRE_USER_CONFIRM 事件并暂停，
 *       结束原因为 PERMISSION_ASKING，工具调用状态标记为 ASKING；</li>
 *   <li><b>审批结果</b>：下一条消息在 metadata 里携带 {@link ConfirmResult} 列表（批准/拒绝），框架据此执行或拒绝；</li>
 *   <li><b>会话免审</b>：「批准并记住」时在 ConfirmResult 里附带一条 ALLOW 规则，框架把它加入本会话权限上下文，
 *       同一工具后续不再询问；</li>
 *   <li><b>超时默认 DENY</b>：挂起超过 60 秒才答复，按拒绝处理（fail-safe）。</li>
 * </ul>
 * 回复约定：「批准」「拒绝」「批准并记住」。
 */
public interface ApprovalAgent extends Agent {

    List<String> HIGH_RISK = List.of("send_email", "delete_file", "shell_execute", "publish_article");
    Duration APPROVAL_TIMEOUT = Duration.ofSeconds(60);

    /** 模拟的业务工具：真实系统里这些会产生不可逆的副作用。 */
    class OfficeTools {

        @Tool(name = "send_email", description = "发送邮件（高风险：对外发送）")
        public String sendEmail(@ToolParam(name = "to", description = "收件人") String to,
                                @ToolParam(name = "subject", description = "主题") String subject) {
            return "邮件已发送 → " + to + "《" + subject + "》（模拟）";
        }

        @Tool(name = "delete_file", description = "删除文件（高风险：不可恢复）")
        public String deleteFile(@ToolParam(name = "path", description = "文件路径") String path) {
            return "文件已删除: " + path + "（模拟）";
        }

        @Tool(name = "shell_execute", description = "执行 Shell 命令（高风险）")
        public String shell(@ToolParam(name = "command", description = "命令") String command) {
            return "已执行: " + command + "（模拟）";
        }

        @Tool(name = "publish_article", description = "发布文章到公众号（高风险：公开发布）")
        public String publish(@ToolParam(name = "title", description = "标题") String title) {
            return "文章已发布《" + title + "》（模拟）";
        }
    }

    class Impl implements ApprovalAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();
        private RuntimeContext ctx = newCtx();
        private List<ToolUseBlock> pending = List.of();
        private Instant pendingSince;

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new CommonTools());
            toolkit.registerTool(new OfficeTools());
            this.agent = ReActAgent.builder()
                    .name("approval")
                    .sysPrompt("你是办公助手，可以发邮件、删文件、执行命令、发布文章、查天气。收件人、主题等信息不全时自行合理假设（如 team@company.com），不要向用户追问，直接调用工具完成任务 —— 是否放行由审批流程决定。")
                    .model(model)
                    .toolkit(toolkit)
                    .permissionContext(permissionPolicy())
                    .maxIters(4)
                    .build();
        }

        /**
         * 风险分级：只读工具配 ALLOW 白名单；其余工具（高风险）不配规则，落到引擎的默认分支 → ASK。
         * <p>
         * 注意引擎优先级：DENY 规则 &gt; ASK 规则 &gt; 工具自检 &gt; ALLOW 规则 &gt; 默认 ASK。
         * 如果给高风险工具显式配 ASK 规则，「批准并记住」追加的 ALLOW 规则将永远被它压住；
         * 所以这里用「白名单放行 + 默认询问」的写法，会话免审才能生效。
         */
        private static PermissionContextState permissionPolicy() {
            PermissionContextState.Builder b = PermissionContextState.builder().mode(PermissionMode.DEFAULT);
            for (String tool : List.of("get_weather", "calculator", "get_time")) {
                b.addAllowRule(tool, new PermissionRule(tool, null, PermissionBehavior.ALLOW, "policy"));
            }
            return b.build();
        }

        @Override
        public String name() {
            return "Step20 · 审批门禁（风险分级 + 审批挂起）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            String text = input.trim();
            List<Msg> msgs;

            if (!pending.isEmpty()) {
                boolean timeout = Duration.between(pendingSince, Instant.now()).compareTo(APPROVAL_TIMEOUT) > 0;
                boolean remember = text.startsWith("批准并记住");
                boolean approve = !timeout && (remember || text.startsWith("批准"));
                boolean deny = timeout || text.startsWith("拒绝");
                if (!approve && !deny) {
                    log.add("guard", "仍在等待审批", "待审批：" + names(pending) + "。请回复「批准」「拒绝」或「批准并记住」");
                    return "有待审批的操作：" + names(pending) + "，请回复「批准」「拒绝」或「批准并记住」。";
                }
                if (timeout) {
                    log.add("guard", "审批超时 → 默认 DENY", "挂起超过 " + APPROVAL_TIMEOUT.toSeconds() + " 秒，按拒绝处理（fail-safe）");
                } else {
                    log.add("guard", "人工决定：" + (remember ? "批准并记住（会话免审）" : approve ? "批准" : "拒绝"), names(pending));
                }
                List<ConfirmResult> results = pending.stream().map(call -> remember
                        ? new ConfirmResult(true, call, List.of(new PermissionRule(call.getName(), null, PermissionBehavior.ALLOW, "session")))
                        : new ConfirmResult(approve, call)).toList();
                msgs = List.of(Msg.builder().role(MsgRole.USER).name("user")
                        .textContent(approve ? "批准" : "拒绝")
                        .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                        .build());
                pending = List.of();
            } else {
                msgs = List.of(new UserMessage(input));
            }

            Tracer.Result r = Tracer.of(log).run(agent.streamEvents(msgs, ctx));
            if (r.is(GenerateReason.PERMISSION_ASKING) && r.msg() != null) {
                pending = r.msg().getContentBlocks(ToolUseBlock.class).stream()
                        .filter(t -> t.getState() == ToolCallState.ASKING).toList();
                pendingSince = Instant.now();
                log.add("thought", "权限检查", "高风险工具不在 ALLOW 白名单 → 默认 ASK，挂起等待审批，超时 "
                        + APPROVAL_TIMEOUT.toSeconds() + " 秒默认拒绝");
                return "⏸️ 高风险操作需要你审批：\n" + pending.stream()
                        .map(t -> "- " + t.getName() + " " + t.getInput()).collect(Collectors.joining("\n"))
                        + "\n请回复「批准」「拒绝」或「批准并记住」。";
            }
            return r.text();
        }

        private static String names(List<ToolUseBlock> calls) {
            return calls.stream().map(ToolUseBlock::getName).collect(Collectors.joining(", "));
        }

        private static RuntimeContext newCtx() {
            return RuntimeContext.builder().userId("toy").sessionId("step20-" + UUID.randomUUID()).build();
        }

        @Override
        public void reset() {
            agent.clearContext(ctx);
            ctx = newCtx();
            pending = List.of();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
