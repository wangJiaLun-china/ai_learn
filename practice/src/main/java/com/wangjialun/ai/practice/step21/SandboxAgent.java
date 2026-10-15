package com.wangjialun.ai.practice.step21;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.kit.WorkFiles;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step21 · 沙箱。命令依次过策略、黑名单、工作区边界、路径规范化，任何一层拒绝就停。
 */
public interface SandboxAgent extends Agent {

    /**
     * 一层校验的结论。
     *
     * @param allowed 是否放行
     * @param layer 拦截层号，0 表示四层都过了
     * @param reason 结论说明
     */
    record Verdict(boolean allowed, int layer, String reason) {
        static Verdict pass() {
            return new Verdict(true, 0, "四层校验全部通过");
        }

        static Verdict deny(int layer, String reason) {
            return new Verdict(false, layer, reason);
        }
    }

    class Impl implements SandboxAgent {

        private static final List<String> DENYLIST = List.of(
                "rm -rf", "sudo", "shutdown", "reboot", "mkfs", "dd if=", ":(){", "chmod 777");
        private static final String SYSTEM = """
                你讲解沙箱。只输出 {"final":"回答"}。想看拦截，请用户发「执行：命令」。
                """;

        private final ChatModel model;
        private final List<Message> history = new ArrayList<>();
        private final Path workspace;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
            this.workspace = WorkFiles.resolve("sandbox");
            try {
                Files.createDirectories(workspace);
                Files.writeString(workspace.resolve("hello.txt"), "practice sandbox\n");
            } catch (IOException ignored) {
                // 目录建失败时，后续执行会在轨迹里说明
            }
        }

        @Override
        public String name() {
            return "Step21 · 沙箱纵深防御（策略 → 黑名单 → 边界 → 规范化，四层拦截）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            history.add(Message.user(input));
            if (input.startsWith("执行：") || input.startsWith("执行:")) {
                return run(input.substring(3).trim());
            }
            Object parsed = Texts.parseLenient(call());
            String answer = parsed instanceof Map<?, ?> map && Json.str(map, "final") != null ? Json.str(map, "final") : String.valueOf(parsed);
            history.add(Message.assistant(answer));
            trace.add("final", "直接作答", "普通对话不走沙箱");
            return answer;
        }

        private String run(String command) {
            trace.add("action", "shell_execute 请求", command);
            trace.add("thought", "第 1 层 · 策略校验", "当前策略允许在工作区内执行 shell");
            String lower = command.toLowerCase();
            for (String bad : DENYLIST) {
                if (lower.contains(bad)) {
                    return deny(2, "命中危险命令特征「" + bad + "」");
                }
            }
            trace.add("thought", "第 2 层 · 内容黑名单", "未命中破坏性命令特征");
            Matcher absolute = Pattern.compile("(?<![\\w.])/(?:[\\w.-]+/)*[\\w.-]+").matcher(command);
            while (absolute.find()) {
                Path path = Path.of(absolute.group()).normalize();
                if (!path.startsWith(workspace)) {
                    return deny(3, "绝对路径 " + absolute.group() + " 越出工作区");
                }
            }
            trace.add("thought", "第 3 层 · 空间边界", "路径位于工作区 " + workspace.getFileName());
            if (command.contains("..")) {
                return deny(4, "检测到 ../ ，规范化后会逃出工作区");
            }
            trace.add("thought", "第 4 层 · 路径规范化", "没有相对跳转");
            String output = command.startsWith("ls") ? "hello.txt" : command.startsWith("cat") ? "practice sandbox" : "命令已模拟执行";
            trace.add("observation", "执行完成", output);
            history.add(Message.assistant("已执行（沙箱内）"));
            return "命令通过四层校验，已在沙箱内执行：\n$ " + command + "\n" + output;
        }

        private String deny(int layer, String why) {
            trace.add("guard", "第 " + layer + " 层 · 拦截", why);
            history.add(Message.assistant("已拦截"));
            return "已在第 " + layer + " 层拦截：" + why;
        }

        private String call() {
            List<Message> request = new ArrayList<>();
            request.add(Message.system(SYSTEM));
            request.addAll(history);
            return model.chat(request).trim();
        }

        @Override
        public void reset() {
            history.clear();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
