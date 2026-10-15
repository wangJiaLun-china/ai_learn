package com.wangjialun.ai.practice.step20;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step20 · 审批门禁。只读工具直接放行，写操作先挂起，等人回复批准、拒绝或批准并记住。
 */
public interface ApprovalAgent extends Agent {

    enum Mode {
        /** 安全工具自动放行，高危工具问人。 */
        AUTO_MANUAL_SAFE,
        /** 所有外部动作都要问。 */
        ALWAYS_ASK,
        /** 全部自动执行。 */
        FULL_AUTO
    }

    class Impl implements ApprovalAgent {

        private static final String SYSTEM = """
                可用工具：get_weather 只读安全；send_email、delete_file 是高危写操作。
                需要工具时只输出 {"tool":"工具名","arguments":"参数摘要"}。
                不需要时只输出 {"final":"最终答案"}。
                """;

        private final ChatModel model;
        private final List<Message> history = new ArrayList<>();
        /** 本会话已「批准并记住」的高危工具。 */
        private final Set<String> trusted = new HashSet<>();
        /** 挂起中的工具名。 */
        private String pendingTool;
        /** 挂起中的参数摘要。 */
        private String pendingArgs;
        private final Mode mode = Mode.AUTO_MANUAL_SAFE;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step20 · 审批门禁（高危操作先过人：批准 / 拒绝 / 批准并记住）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            if (pendingTool != null) {
                String tool = pendingTool;
                String args = pendingArgs;
                pendingTool = null;
                return resolve(tool, args, input.trim());
            }
            history.add(Message.user(input));
            return decide();
        }

        private String decide() {
            Object parsed = Texts.parseLenient(call(SYSTEM));
            if (!(parsed instanceof Map<?, ?> decision)) {
                history.add(Message.assistant(String.valueOf(parsed)));
                trace.add("final", "直接作答", "无工具需求");
                return String.valueOf(parsed);
            }
            String tool = Json.str(decision, "tool");
            if (tool == null) {
                String answer = blankTo(Json.str(decision, "final"), "");
                history.add(Message.assistant(answer));
                trace.add("final", "直接作答", "无需工具");
                return answer;
            }
            String args = decision.get("arguments") == null ? "" : String.valueOf(decision.get("arguments"));
            trace.add("action", "模型发起工具调用", tool + " " + args);
            boolean dangerous = isDangerous(tool);
            boolean ask = mode == Mode.ALWAYS_ASK || (mode == Mode.AUTO_MANUAL_SAFE && dangerous && !trusted.contains(tool));
            trace.add("thought", "权限矩阵评估", tool + " → " + (dangerous ? "高危" : "安全")
                    + " · 模式 " + mode + (trusted.contains(tool) ? " · 命中会话免审" : ""));
            if (ask) {
                pendingTool = tool;
                pendingArgs = args;
                trace.add("guard", "审批挂起", "高危操作等待人工决策，超时应按拒绝处理");
                return "即将执行高危操作：" + tool + "\n参数：" + args
                        + "\n请回复：「批准」「拒绝」或「批准并记住」。";
            }
            String result = execute(tool, args);
            trace.add("observation", trusted.contains(tool) && dangerous ? "自动放行（会话免审）" : "自动放行（安全工具）", result);
            return finish(tool, result);
        }

        private String resolve(String tool, String args, String decision) {
            if (decision.startsWith("批准并记住")) {
                trusted.add(tool);
                trace.add("guard", "人工决策 · 批准并记住", tool + " 加入本会话免审名单");
            } else if (decision.startsWith("批准")) {
                trace.add("guard", "人工决策 · 批准", "仅本次放行");
            } else if (decision.startsWith("拒绝")) {
                trace.add("guard", "人工决策 · 拒绝", "操作未发生");
                history.add(Message.user("审批结果：" + tool + " 被拒绝"));
                return "已拒绝执行 " + tool + "。";
            } else {
                pendingTool = tool;
                pendingArgs = args;
                trace.add("guard", "无法识别的决策", "审批仍挂起");
                return "请回复：「批准」「拒绝」或「批准并记住」。";
            }
            String result = execute(tool, args);
            trace.add("observation", "审批放行 · 执行", result);
            history.add(Message.user("审批结果：" + tool + " 已批准并执行"));
            return finish(tool, result);
        }

        private String finish(String tool, String result) {
            Object parsed = Texts.parseLenient(call(SYSTEM + "\n工具 " + tool + " 的执行结果：" + result + "。请据此作答。"));
            String answer = parsed instanceof Map<?, ?> map ? blankTo(Json.str(map, "final"), String.valueOf(parsed)) : String.valueOf(parsed);
            history.add(Message.assistant(answer));
            return answer;
        }

        private String call(String system) {
            List<Message> request = new ArrayList<>();
            request.add(Message.system(system));
            request.addAll(history);
            return model.chat(request).trim();
        }

        private static boolean isDangerous(String tool) {
            return switch (tool) {
                case "send_email", "delete_file", "shell_execute", "publish_article" -> true;
                default -> false;
            };
        }

        private static String execute(String tool, String args) {
            return switch (tool) {
                case "send_email" -> "已发送（模拟）：" + args;
                case "delete_file" -> "已删除（模拟）：" + args;
                case "get_weather" -> "晴，26℃（模拟数据）";
                default -> "已执行（模拟）：" + args;
            };
        }

        private static String blankTo(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        @Override
        public void reset() {
            history.clear();
            trusted.clear();
            pendingTool = null;
            pendingArgs = null;
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
