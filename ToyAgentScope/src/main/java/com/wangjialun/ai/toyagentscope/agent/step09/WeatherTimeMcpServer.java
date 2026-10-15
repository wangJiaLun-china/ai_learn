package com.wangjialun.ai.toyagentscope.agent.step09;

import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

/**
 * 一个真正的 MCP Server（stdio 传输）：对外暴露 get_weather / get_time 两个工具。
 * <p>
 * 用官方 MCP Java SDK 实现，独立进程运行：由 Step09 的客户端以子进程方式拉起，
 * 双方通过 stdin/stdout 交换 JSON-RPC（initialize → tools/list → tools/call）。
 * 注意：stdout 是协议通道，服务端绝不能向 stdout 打印日志。
 */
public final class WeatherTimeMcpServer {

    /**
     * 工具注解 readOnlyHint=true：声明「只读、无副作用」。
     * AgentScope 的 McpTool 据此自动放行；未声明只读的远端工具默认需要人工审批（见 Step20）。
     */
    private static final McpSchema.ToolAnnotations READ_ONLY =
            new McpSchema.ToolAnnotations("只读查询", true, false, true, false, false);

    private WeatherTimeMcpServer() {
    }

    public static void main(String[] args) throws Exception {
        JacksonMcpJsonMapper json = new JacksonMcpJsonMapper(new ObjectMapper());

        McpSchema.Tool weather = McpSchema.Tool.builder()
                .name("get_weather")
                .description("查询城市今天的天气")
                .inputSchema(json, """
                        {"type":"object","properties":{"city":{"type":"string","description":"城市名，如 杭州"}},"required":["city"]}
                        """)
                .annotations(READ_ONLY)
                .build();
        McpSchema.Tool time = McpSchema.Tool.builder()
                .name("get_time")
                .description("获取 MCP 服务端的当前时间")
                .inputSchema(json, """
                        {"type":"object","properties":{}}
                        """)
                .annotations(READ_ONLY)
                .build();

        McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(json))
                .serverInfo("toy-weather-time", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(
                        McpServerFeatures.SyncToolSpecification.builder()
                                .tool(weather)
                                .callHandler((exchange, req) -> text(CommonTools.weather(arg(req.arguments(), "city"))))
                                .build(),
                        McpServerFeatures.SyncToolSpecification.builder()
                                .tool(time)
                                .callHandler((exchange, req) -> text(CommonTools.now()))
                                .build())
                .build();

        // stdio 服务端随父进程的 stdin 关闭而结束；这里阻塞主线程保持存活
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        Thread.currentThread().join();
    }

    private static McpSchema.CallToolResult text(String s) {
        return McpSchema.CallToolResult.builder().addTextContent(s).isError(false).build();
    }

    private static String arg(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
