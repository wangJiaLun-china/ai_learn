package com.wangjialun.ai.practice.step25;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.kit.WorkFiles;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Step25 · CLI。模型可以决定执行命令，命令在受限工作区里过四层检查后才真正跑。
 */
public interface CliAgent extends Agent {

    class Impl implements CliAgent {

        private static final Set<String> DENYLIST = Set.of("rm", "sudo", "shutdown", "reboot", "mkfs", "dd", "kill", "pkill");
        private static final Set<String> ALLOWLIST = Set.of("ls", "pwd", "echo", "cat", "date", "wc");

        private final ChatModel model;
        private final Path workspace = WorkFiles.resolve("sandbox", "step25");
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
            prepareWorkspace();
        }

        private void prepareWorkspace() {
            try {
                Files.createDirectories(workspace);
                Path hello = workspace.resolve("hello.txt");
                if (Files.notExists(hello)) {
                    Files.writeString(hello, "你好，这里是练习用的受限工作区。\n", StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
                // 工作区准备失败时，执行阶段会返回错误
            }
        }

        @Override
        public String name() {
            return "Step25 · CLI 智能体（终端 REPL + 沙箱执行器）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            trace.add("tool", "终端会话就绪", "工作区 " + workspace);
            if (input.startsWith("运行：") || input.startsWith("运行:")) {
                String command = input.substring(3).trim();
                return "命令：" + command + "\n结果：\n" + guarded(command);
            }
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("""
                    你是终端里的智能体。当前工作区：%s
                    需要执行命令时只输出 {"tool":"run_command","arguments":{"command":"命令"}}。
                    不需要命令时直接回答。
                    """.formatted(workspace)));
            messages.add(Message.user(input));
            for (int round = 1; round <= 2; round++) {
                String raw = model.chat(messages).trim();
                Object decision = Texts.parseLenient(raw);
                if (decision instanceof Map<?, ?> call && Json.str(call, "tool") != null) {
                    Object arguments = call.get("arguments");
                    String command = arguments instanceof Map<?, ?> map && map.get("command") != null
                            ? String.valueOf(map.get("command")) : "";
                    trace.add("action", "模型发起命令执行", command);
                    String result = guarded(command);
                    messages.add(Message.assistant(raw));
                    messages.add(Message.user("工具 run_command 执行结果: " + result));
                    continue;
                }
                trace.add("final", "最终回答", "模型基于终端上下文作答");
                return raw;
            }
            return "已达最大轮数，循环终止。";
        }

        private String guarded(String command) {
            if (command == null || command.isBlank()) {
                return "（空命令）";
            }
            String lower = command.toLowerCase();
            for (String bad : DENYLIST) {
                if (lower.contains(bad)) {
                    trace.add("guard", "第①层 · 拒绝名单", "命中 " + bad);
                    return "已拦截：命令包含拒绝名单中的 " + bad + "。";
                }
            }
            for (String token : command.split("\\s+")) {
                if (token.contains("..") || token.startsWith("/")) {
                    trace.add("guard", "第②层 · 路径边界", "token " + token + " 越出工作区");
                    return "已拦截：路径 " + token + " 越出受限工作区。";
                }
            }
            String head = command.split("\\s+")[0];
            if (!ALLOWLIST.contains(head)) {
                trace.add("guard", "第③层 · 命令白名单", head + " 不在白名单中");
                return "已拦截：命令 " + head + " 不在白名单中。";
            }
            try {
                Process process = new ProcessBuilder(command.split("\\s+"))
                        .directory(workspace.toFile())
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                process.waitFor(5, TimeUnit.SECONDS);
                trace.add("observation", "沙箱执行 " + command, "cwd=" + workspace + "，退出码 " + process.exitValue());
                return output.isBlank() ? "（无输出）" : output;
            } catch (Exception ex) {
                return "执行失败: " + ex.getMessage();
            }
        }

        @Override
        public void reset() {
            prepareWorkspace();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
