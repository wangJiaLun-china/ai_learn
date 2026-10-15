package com.wangjialun.ai.toyagentscope.agent.step25;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.ToolCalls;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import com.wangjialun.ai.toyagentscope.llm.Models;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Step25 · CLI 智能体 —— 终端 REPL + -p 单命令模式 + 沙箱 Shell 执行器。
 * <p>
 * 同一个智能体两种形态：
 * <ul>
 *   <li>Web：和其他场景一样走 /api/step25/chat；</li>
 *   <li>终端：{@link #main(String[])} —— 不带参数进入 REPL，{@code -p "问题"} 单次执行后退出（Claude Code 风格）。</li>
 * </ul>
 * run_command 是一个 {@link ToolBase}：checkPermissions 做三道检查（黑名单 → 路径边界 → 命令白名单），
 * 通过后用 ProcessBuilder <b>真实执行</b>，工作目录固定为 data/sandbox/step25，5 秒超时。
 */
public interface CliAgent extends Agent {

    Path WORKSPACE = Path.of("data", "sandbox", "step25");
    List<String> DENY = List.of("rm", "sudo", "mv", "chmod", "chown", "curl", "wget", "kill", "shutdown", ">", "|", ";", "&");
    List<String> ALLOW = List.of("ls", "pwd", "echo", "cat", "date", "wc");

    class RunCommandTool extends ToolBase {

        private final TraceLog log;

        RunCommandTool(TraceLog log) {
            super(ToolBase.builder()
                    .name("run_command")
                    .description("在工作区 data/sandbox/step25 中执行一条 Shell 命令（仅 ls/pwd/echo/cat/date/wc）")
                    .inputSchema(Map.of("type", "object",
                            "properties", Map.of("command", Map.of("type", "string", "description", "要执行的命令")),
                            "required", List.of("command"))));
            this.log = log;
        }

        @Override
        public Mono<PermissionDecision> checkPermissions(Map<String, Object> input, PermissionContextState context) {
            String cmd = String.valueOf(input.getOrDefault("command", "")).trim();
            List<String> words = List.of(cmd.split("\\s+"));
            String bad = DENY.stream().filter(d -> words.contains(d) || (d.length() == 1 && cmd.contains(d))).findFirst().orElse(null);
            if (bad != null) return deny("黑名单", "包含危险命令/符号「" + bad + "」");
            if (cmd.contains("..") || words.stream().skip(1).anyMatch(w -> w.startsWith("/") || w.startsWith("~"))) {
                return deny("路径边界", "不允许 .. 或绝对路径，只能访问工作区");
            }
            if (!ALLOW.contains(words.get(0))) return deny("白名单", words.get(0) + " 不在允许列表 " + ALLOW);
            log.add("guard", "安全检查通过", "黑名单 ✓ 路径边界 ✓ 白名单 ✓");
            return Mono.just(PermissionDecision.allow("ok"));
        }

        private Mono<PermissionDecision> deny(String layer, String reason) {
            log.add("guard", "⛔ " + layer + "拦截", reason);
            return Mono.just(PermissionDecision.deny(layer + "拦截：" + reason));
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            String cmd = String.valueOf(param.getInput().getOrDefault("command", "")).trim();
            return Mono.fromCallable(() -> ToolResultBlock.text(exec(cmd)));
        }

        static String exec(String cmd) throws IOException, InterruptedException {
            Process p = new ProcessBuilder(new ArrayList<>(List.of(cmd.split("\\s+"))))
                    .directory(WORKSPACE.toFile())
                    .redirectErrorStream(true)
                    .start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null && out.length() < 4000) out.append(line).append('\n');
            }
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "执行超时（5 秒）";
            }
            return out.toString().trim() + (p.exitValue() == 0 ? "" : "\n(exit " + p.exitValue() + ")");
        }
    }

    class Impl implements CliAgent {

        private final TraceLog log = new TraceLog();
        private final RunCommandTool runCommand = new RunCommandTool(log);
        private final Toolkit toolkit = new Toolkit();
        private final ReActAgent agent;

        public Impl(Model model) {
            try {
                Files.createDirectories(WORKSPACE);
                Files.writeString(WORKSPACE.resolve("hello.txt"), "Hello, ToyAgentScope CLI!\n第二行\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("初始化工作区失败", e);
            }
            toolkit.registerAgentTool(runCommand);
            toolkit.registerTool(new CommonTools());
            this.agent = ReActAgent.builder()
                    .name("cli")
                    .sysPrompt("你是一个终端助手，可以用 run_command 在工作区执行 ls/pwd/echo/cat/date/wc，回答简洁。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(4)
                    .build();
        }

        @Override
        public String name() {
            return "Step25 · CLI 智能体（REPL + 沙箱 Shell）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            String text = input.trim();
            if (text.startsWith("运行：") || text.startsWith("运行:")) {
                String cmd = text.substring(3).trim();
                Map<String, Object> args = Map.of("command", cmd);
                log.add("tool", "run_command", cmd);
                PermissionDecision d = runCommand.checkPermissions(args, PermissionContextState.builder().build()).block();
                if (d != null && d.getBehavior() == PermissionBehavior.DENY) return "⛔ " + d.getMessage();
                String out = ToolCalls.callText(toolkit, "run_command", args);
                log.add("observation", "命令输出", out);
                log.add("final", "Final Answer", out);
                return out;
            }
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            return Tracer.of(log).run(agent.streamEvents(List.of(new UserMessage(input)), ctx)).text();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }

    /**
     * 终端入口：
     * <pre>
     *   ./gradlew -q runCli                         # REPL
     *   ./gradlew -q runCli --args='-p "运行：ls"'   # 单命令模式
     * </pre>
     */
    static void main(String[] args) throws IOException {
        Impl cli = new Impl(Models.model(Models.serverDefault()));
        if (args.length >= 2 && "-p".equals(args[0])) {
            System.out.println(cli.chat(String.join(" ", List.of(args).subList(1, args.length))));
            System.exit(0);
        }
        System.out.println("ToyAgentScope CLI（AgentScope Java 2.0）· 输入 exit 退出");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("\n› ");
            String line = in.readLine();
            if (line == null || "exit".equalsIgnoreCase(line.trim())) break;
            if (line.isBlank()) continue;
            System.out.println(cli.chat(line));
        }
        System.exit(0);
    }
}
