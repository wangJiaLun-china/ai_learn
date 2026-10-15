package com.wangjialun.ai.practice.step24;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step24 · Hooks。工具执行前可以拦截或改写，执行后可以脱敏。摘掉钩子，管线就直通。
 */
public interface HooksAgent extends Agent {

    /**
     * 一个钩子。
     *
     * @param name 钩子名
     * @param phase before 或 after
     * @param match 作用的工具名，* 表示全部
     * @param handler 返回 null 表示不改；以 DENY: 开头表示拦截
     */
    record Hook(String name, String phase, List<String> match, Handler handler) {
        @FunctionalInterface
        interface Handler {
            String apply(String tool, String payload);
        }
    }

    class Impl implements HooksAgent {

        private static final String SYSTEM = """
                你讲解钩子。只输出 {"final":"回答"}。
                """;

        private final ChatModel model;
        private final List<Hook> hooks = new ArrayList<>();
        /** before 审计钩子写下的调用记录。 */
        private final List<String> audit = new ArrayList<>();
        private boolean enabled = true;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
            hooks.add(new Hook("审计", "before", List.of("*"), (tool, payload) -> {
                audit.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + tool + "  args=" + payload);
                return null;
            }));
            hooks.add(new Hook("风控-敏感外发", "before", List.of("send_sms"), (tool, payload) ->
                    payload.contains("密码") || payload.contains("验证码") ? "DENY:短信内容含敏感信息，禁止外发" : null));
            hooks.add(new Hook("脱敏", "after", List.of("*"), (tool, payload) -> {
                String masked = payload.replaceAll("(1[3-9])\\d{9}", "$1*********");
                masked = Pattern.compile("([\\w.]+)@([\\w.]+)").matcher(masked)
                        .replaceAll(match -> match.group(1).charAt(0) + "***@" + match.group(2));
                return masked.equals(payload) ? null : masked;
            }));
        }

        @Override
        public String name() {
            return "Step24 · Hooks 钩子体系（before 拦截/改写 · after 脱敏）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            if (input.startsWith("钩子：") || input.startsWith("钩子:")) {
                enabled = input.contains("开");
                trace.add("action", enabled ? "钩子已全部挂载" : "钩子已全部摘除", "enabled=" + enabled);
                return enabled ? "钩子已挂回。" : "钩子已全部摘除，工具现在不再脱敏或拦截。";
            }
            if (input.contains("审计日志")) {
                String body = audit.isEmpty() ? "（暂无）" : String.join("\n", audit);
                trace.add("observation", "审计日志", body);
                return "审计日志：\n" + body;
            }
            if (input.startsWith("查询：") || input.startsWith("查询:")) {
                return pipeline("query_user", Json.write(Map.of("name", input.substring(3).trim())),
                        "{\"name\":\"张三\",\"phone\":\"13812345678\",\"email\":\"zhangsan@example.com\",\"level\":\"VIP\"}");
            }
            if (input.startsWith("发送：") || input.startsWith("发送:")) {
                String payload = input.substring(3).trim();
                Matcher mobile = Pattern.compile("1[3-9]\\d{9}").matcher(payload);
                String phone = mobile.find() ? mobile.group() : "13800000000";
                return pipeline("send_sms", Json.write(Map.of("phone", phone, "message", payload)), "短信已提交网关");
            }
            Object parsed = Texts.parseLenient(model.chat(List.of(Message.system(SYSTEM), Message.user(input))).trim());
            String answer = parsed instanceof Map<?, ?> map && Json.str(map, "final") != null ? Json.str(map, "final") : String.valueOf(parsed);
            trace.add("final", "讲解回答", "可试用查询、发送、开关钩子和审计日志");
            return answer;
        }

        private String pipeline(String tool, String args, String toolResult) {
            trace.add("action", "工具调用 " + tool, "参数 " + args);
            String payload = args;
            for (Hook hook : matching("before", tool)) {
                String out = hook.handler().apply(tool, payload);
                if (out != null && out.startsWith("DENY:")) {
                    trace.add("guard", "before · " + hook.name() + " → 拦截", out.substring(5));
                    return "工具 " + tool + " 被 before 钩子「" + hook.name() + "」拦截：" + out.substring(5);
                }
                trace.add("thought", "before · " + hook.name() + (out == null ? " → 放行" : " → 改写参数"),
                        out == null ? "未干预" : payload + " → " + out);
                if (out != null) {
                    payload = out;
                }
            }
            trace.add("observation", "工具执行完成", toolResult);
            String output = toolResult;
            for (Hook hook : matching("after", tool)) {
                String out = hook.handler().apply(tool, output);
                if (out != null) {
                    trace.add("thought", "after · " + hook.name() + " → 改写输出", "输出已加工");
                    output = out;
                }
            }
            return "钩子管线执行完毕：\n最终输出：" + output;
        }

        private List<Hook> matching(String phase, String tool) {
            if (!enabled) {
                return List.of();
            }
            return hooks.stream()
                    .filter(hook -> hook.phase().equals(phase))
                    .filter(hook -> hook.match().contains("*") || hook.match().contains(tool))
                    .toList();
        }

        @Override
        public void reset() {
            audit.clear();
            enabled = true;
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
