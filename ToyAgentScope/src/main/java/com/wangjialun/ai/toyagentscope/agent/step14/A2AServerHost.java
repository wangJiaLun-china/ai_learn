package com.wangjialun.ai.toyagentscope.agent.step14;

import com.wangjialun.ai.toyagentscope.Application;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.llm.Models;
import com.sun.net.httpserver.HttpExchange;
import io.a2a.util.Utils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import io.agentscope.core.a2a.server.transport.DeploymentProperties;
import io.agentscope.core.a2a.server.transport.TransportWrapper;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「远端」A2A 代理宿主：把两个 AgentScope 智能体（translator / weather）发布成标准 A2A 服务。
 * <p>
 * 生产中它们部署在别的机器上；教学里挂在同一个 HttpServer 的 /a2a/{name} 下，
 * 但调用方（Step14）走的是真实的 HTTP + A2A 协议：
 * <ul>
 *   <li>GET  /a2a/{name}/.well-known/agent-card.json —— Agent Card 名片（能力、URL、传输方式）；</li>
 *   <li>POST /a2a/{name} —— JSON-RPC（message/send 一次性响应；message/stream 用 SSE 推送任务事件）。</li>
 * </ul>
 * {@link AgentScopeA2aServer} 不自己监听端口，只给出 TransportWrapper，由我们把 HTTP 请求转交给它。
 */
public final class A2AServerHost {

    /** 远端代理目录：名字 → (描述, 系统提示词)。 */
    static final Map<String, String[]> AGENTS = new LinkedHashMap<>();

    static {
        AGENTS.put("translator", new String[]{"中英互译代理：把用户给的文本翻译成目标语言",
                "你是专业翻译代理。只输出译文，不要解释。"});
        AGENTS.put("weather", new String[]{"天气顾问代理：查询城市天气并给出户外活动建议",
                "你是天气顾问代理。先用 get_weather 查天气，再用两句话给出是否适合户外运动的建议。未指明城市默认北京。"});
    }

    private static final Map<String, AgentScopeA2aServer> SERVERS = new ConcurrentHashMap<>();

    private A2AServerHost() {
    }

    /** 本机 A2A 代理的基础 URL。 */
    public static String baseUrl(String name) {
        return "http://localhost:" + Application.port + "/a2a/" + name;
    }

    private static AgentScopeA2aServer server(String name) {
        return SERVERS.computeIfAbsent(name, n -> {
            String[] meta = AGENTS.get(n);
            Toolkit toolkit = new Toolkit();
            if ("weather".equals(n)) toolkit.registerTool(new CommonTools());
            ReActAgent.Builder agent = ReActAgent.builder()
                    .name(n)
                    .description(meta[0])
                    .sysPrompt(meta[1])
                    .model(Models.model(Models.serverDefault()))
                    .toolkit(toolkit)
                    .maxIters(3);
            AgentScopeA2aServer s = AgentScopeA2aServer.builder(agent)
                    .agentCard(new ConfigurableAgentCard.Builder().name(n).description(meta[0]).version("1.0.0").build())
                    .deploymentProperties(new DeploymentProperties("localhost", Application.port, "/a2a/" + n))
                    .build();
            s.postEndpointReady();
            return s;
        });
    }

    /** HTTP 入口：/a2a/{name}[/.well-known/agent-card.json] */
    @SuppressWarnings("unchecked")
    public static void handle(HttpExchange exchange) {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            String name = parts.length > 2 ? parts[2] : "";
            if (!AGENTS.containsKey(name)) {
                Application.respond(exchange, 404, "{\"error\":\"unknown agent " + name + "\"}");
                return;
            }
            AgentScopeA2aServer server = server(name);
            if (path.endsWith("/.well-known/agent-card.json")) {
                Application.respond(exchange, 200, Utils.OBJECT_MAPPER.writeValueAsString(server.getAgentCard()));
                return;
            }
            String body = Application.readBody(exchange);
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, String.join(",", v)));
            TransportWrapper<String, Object> wrapper = server.getTransportWrapper("JSONRPC");
            Object result = wrapper.handleRequest(body, headers, Map.of());
            if (result instanceof Flux<?> flux) {
                // message/stream：用 SSE 逐条推送 JSON-RPC 响应
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                List<Object> events = (List<Object>) (List<?>) flux.collectList().block();
                for (Object ev : events == null ? List.of() : events) {
                    os.write(("data: " + Utils.OBJECT_MAPPER.writeValueAsString(ev) + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
                os.close();
            } else {
                Application.respond(exchange, 200, Utils.OBJECT_MAPPER.writeValueAsString(result));
            }
        } catch (Exception e) {
            try {
                Application.respond(exchange, 500, "{\"error\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}");
            } catch (IOException ignored) {
                // 响应已不可写
            }
        }
    }
}
