package com.wangjialun.ai.toyagentscope.agent.step09;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step09 · MCP 协议 —— 工具标准化接口（真·MCP，跨进程）。
 * <p>
 * 手写版：在同一个 JVM 里用接口模拟 tools/list、tools/call。
 * <p>
 * 框架版：真实的 Model Context Protocol ——
 * <ol>
 *   <li>{@link WeatherTimeMcpServer} 是一个独立的 MCP Server 进程（官方 MCP Java SDK，stdio 传输）；</li>
 *   <li>{@link McpClientBuilder} 以子进程方式拉起它，完成 initialize 握手；</li>
 *   <li>{@code toolkit.registerMcpClient(client)} 调用 tools/list，把远端工具注册成本地可调用工具；</li>
 *   <li>模型发起调用时，Toolkit 通过 tools/call 转发给 MCP Server 执行。</li>
 * </ol>
 * 智能体完全不知道工具在哪个进程、用什么语言实现 —— 这就是 MCP 的「工具与智能体解耦」。
 * MCP Client 是进程级单例：所有会话共享同一个 MCP Server 子进程。
 */
public interface McpAgent extends Agent {

    class Impl implements McpAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerMcpClient(SharedClient.get()).block();
            this.agent = ReActAgent.builder()
                    .name("mcp")
                    .sysPrompt("你是一个助手，可以通过 MCP 工具查询天气和时间。需要实时信息时调用工具后再回答。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(4)
                    .build();
        }

        @Override
        public String name() {
            return "Step09 · MCP 协议智能体（工具标准化）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            log.add("mcp", "tools/list 发现工具", "MCP Server「toy-weather-time」(stdio 子进程) 提供："
                    + String.join(", ", agent.getToolkit().getToolNames()));
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            return Tracer.of(log)
                    .actionType(tool -> "tool")
                    .observationType(tool -> "observation")
                    .run(agent.streamEvents(List.of(new UserMessage(input)), ctx))
                    .text();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }

    /** 进程级共享的 MCP Client：首次使用时拉起 MCP Server 子进程。 */
    final class SharedClient {

        private static volatile McpClientWrapper client;

        private SharedClient() {
        }

        static McpClientWrapper get() {
            if (client == null) {
                synchronized (SharedClient.class) {
                    if (client == null) {
                        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
                        String cp = System.getProperty("java.class.path");
                        client = McpClientBuilder.create("toy-weather-time")
                                .stdioTransport(java, "-cp", cp, WeatherTimeMcpServer.class.getName())
                                .timeout(Duration.ofSeconds(30))
                                .buildAsync()
                                .block();
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                client.close();
                            } catch (Exception ignored) {
                                // 进程退出时尽力关闭
                            }
                        }));
                    }
                }
            }
            return client;
        }
    }
}
