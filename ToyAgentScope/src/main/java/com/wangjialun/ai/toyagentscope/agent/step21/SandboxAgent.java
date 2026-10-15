package com.wangjialun.ai.toyagentscope.agent.step21;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.ToolCalls;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Step21 · 沙箱纵深防御 —— 四层拦截：黑名单 / 策略白名单 / 路径边界 / 规范化。
 * <p>
 * 框架版：把四层检查写进工具自己的 {@link ToolBase#checkPermissions} —— 这是 AgentScope 权限系统
 * 留给工具的钩子：模型每次想调用 shell_execute，框架都会先问工具「这次调用允许吗」，
 * 返回 DENY 则框架直接写回一条 DENIED 结果，命令根本不会执行。
 * <ol>
 *   <li>黑名单：rm -rf、sudo、mkfs、curl … 直接拒绝；</li>
 *   <li>策略：只读模式，只允许 ls / cat / pwd / echo / head / wc；</li>
 *   <li>边界：不允许绝对路径（/etc/passwd）；</li>
 *   <li>规范化：normalize 后仍必须落在 sandbox 目录内（防 sandbox/../../etc/passwd 穿越）。</li>
 * </ol>
 * 「执行：命令」直接走工具（先 checkPermissions 再执行，结果可复现）；其他输入交给模型，模型调用时同样经过四层检查。
 * 生产中还可以叠加 HarnessAgent 的 SandboxFilesystemSpec（Docker 隔离）作为第五层。
 */
public interface SandboxAgent extends Agent {

    Path SANDBOX = Path.of("data", "sandbox");
    List<String> DENYLIST = List.of("rm ", "rm -rf", "sudo", "mkfs", "chmod", "curl", "wget", "> /", "shutdown");
    List<String> ALLOW_COMMANDS = List.of("ls", "cat", "pwd", "echo", "head", "wc");

    class SandboxShellTool extends ToolBase {

        private final TraceLog log;

        SandboxShellTool(TraceLog log) {
            super(ToolBase.builder()
                    .name("shell_execute")
                    .description("在沙箱目录 data/sandbox 中执行只读 Shell 命令（ls/cat/pwd/echo/head/wc）")
                    .inputSchema(Map.of("type", "object",
                            "properties", Map.of("command", Map.of("type", "string", "description", "要执行的命令")),
                            "required", List.of("command"))));
            this.log = log;
        }

        /** 四层纵深防御：任意一层 DENY 即终止。 */
        @Override
        public Mono<PermissionDecision> checkPermissions(Map<String, Object> input, PermissionContextState context) {
            String cmd = String.valueOf(input.getOrDefault("command", "")).trim();
            String hit = DENYLIST.stream().filter(cmd::contains).findFirst().orElse(null);
            if (hit != null) return deny("① 黑名单", "命中危险片段「" + hit.trim() + "」");
            log.add("thought", "① 黑名单 ✓", "未命中危险命令");

            String head = cmd.split("\\s+")[0];
            if (!ALLOW_COMMANDS.contains(head)) return deny("② 策略", "只读模式不允许命令 " + head + "，白名单：" + ALLOW_COMMANDS);
            log.add("thought", "② 策略 ✓", head + " 在只读白名单内");

            List<String> args = Stream.of(cmd.split("\\s+")).skip(1).filter(a -> !a.startsWith("-")).toList();
            String abs = args.stream().filter(a -> a.startsWith("/") || a.startsWith("~")).findFirst().orElse(null);
            if (abs != null) return deny("③ 边界", "禁止访问沙箱外的绝对路径 " + abs);
            log.add("thought", "③ 边界 ✓", "无绝对路径");

            for (String a : args) {
                if (!"echo".equals(head) && !resolve(a).startsWith(SANDBOX.toAbsolutePath().normalize())) {
                    return deny("④ 规范化", a + " 规范化后为 " + resolve(a) + "，逃逸出沙箱");
                }
            }
            log.add("thought", "④ 规范化 ✓", "所有路径规范化后仍在 " + SANDBOX);
            return Mono.just(PermissionDecision.allow("四层检查通过"));
        }

        private Mono<PermissionDecision> deny(String layer, String reason) {
            log.add("guard", layer + " ✗ 拦截", reason);
            return Mono.just(PermissionDecision.deny(layer + "拦截：" + reason));
        }

        /** 路径参数一律相对项目根解析（sandbox/xxx 或 data/sandbox/xxx 都视为沙箱内）。 */
        static Path resolve(String arg) {
            String a = arg.startsWith("sandbox") ? "data/" + arg : arg;
            Path base = a.startsWith("data/sandbox") ? Path.of("") : SANDBOX;
            return base.resolve(a).toAbsolutePath().normalize();
        }

        /** 通过检查后才会走到这里：用 Java 实现只读命令（教学模拟，不起子进程）。 */
        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            String cmd = String.valueOf(param.getInput().getOrDefault("command", "")).trim();
            String[] parts = cmd.split("\\s+");
            String target = Stream.of(parts).skip(1).filter(a -> !a.startsWith("-")).findFirst().orElse(".");
            String out;
            try {
                out = switch (parts[0]) {
                    case "pwd" -> SANDBOX.toAbsolutePath().normalize().toString();
                    case "echo" -> cmd.substring(4).trim();
                    case "ls" -> {
                        try (Stream<Path> s = Files.list(resolve(target))) {
                            yield s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.joining("  "));
                        }
                    }
                    case "cat", "head" -> Files.readString(resolve(target), StandardCharsets.UTF_8);
                    case "wc" -> Files.readAllLines(resolve(target)).size() + " " + target;
                    default -> "不支持的命令";
                };
            } catch (IOException e) {
                out = "执行失败: " + e.getMessage();
            }
            return Mono.just(ToolResultBlock.text(out));
        }
    }

    class Impl implements SandboxAgent {

        private final TraceLog log = new TraceLog();
        private final SandboxShellTool shell = new SandboxShellTool(log);
        private final Toolkit toolkit = new Toolkit();
        private final ReActAgent agent;

        public Impl(Model model) {
            try {
                Files.createDirectories(SANDBOX);
                Files.writeString(SANDBOX.resolve("hello.txt"), "Hello from ToyAgentScope sandbox!\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("初始化沙箱失败", e);
            }
            toolkit.registerAgentTool(shell);
            this.agent = ReActAgent.builder()
                    .name("sandbox")
                    .sysPrompt("你是运维助手，可以用 shell_execute 在沙箱中执行只读命令。用户只是提问时直接回答，不要执行命令。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(3)
                    .build();
        }

        @Override
        public String name() {
            return "Step21 · 沙箱纵深防御（四层拦截）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            String text = input.trim();
            if (text.startsWith("执行：") || text.startsWith("执行:")) {
                String cmd = text.substring(3).trim();
                log.add("action", "shell_execute", cmd);
                Map<String, Object> args = Map.of("command", cmd);
                PermissionDecision d = shell.checkPermissions(args, PermissionContextState.builder().build()).block();
                if (d != null && d.getBehavior() == PermissionBehavior.DENY) {
                    return "⛔ 已拦截：" + d.getMessage();
                }
                String out = ToolCalls.callText(toolkit, "shell_execute", args);
                log.add("observation", "沙箱输出", out);
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
}
