package com.wangjialun.ai.practice.step09;

import com.wangjialun.ai.practice.kit.CityWeather;
import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step09 · MCP。工具住在 Server 里，智能体只通过 tools/list 和 tools/call 使用它们。
 */
public interface McpAgent extends Agent {

    class Impl implements McpAgent {

        private final ChatModel model;
        private final McpClient client = new McpClient(new WeatherTimeServer());
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step09 · MCP 协议（工具标准化接口）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            List<Map<String, String>> tools = client.listTools();
            trace.add("mcp", "tools/list 工具发现",
                    "从 MCP Server 动态获取 " + tools.size() + " 个工具: "
                            + tools.stream().map(t -> t.get("name")).reduce((a, b) -> a + ", " + b).orElse(""));
            StringBuilder docs = new StringBuilder();
            for (Map<String, String> tool : tools) {
                docs.append("- ").append(tool.get("name")).append("(").append(tool.get("parameters"))
                        .append("): ").append(tool.get("description")).append("\n");
            }
            String decide = """
                    你可以调用 MCP 工具：
                    %s
                    需要工具时只输出 {"method":"tools/call","params":{"name":"工具名","arguments":{"参数":"值"}}}。
                    否则直接自然语言回答。
                    """.formatted(docs);
            String raw = model.chat(List.of(Message.system(decide), Message.user(input))).trim();
            Object decision = Texts.parseLenient(raw);
            if (decision instanceof Map<?, ?> call && "tools/call".equals(Json.str(call, "method"))) {
                Map<?, ?> params = (Map<?, ?>) call.get("params");
                String name = String.valueOf(params.get("name"));
                String args = Json.write(params.get("arguments"));
                trace.add("tool", "tools/call 工具调用", name + " " + args);
                String result = client.callTool(name, args);
                trace.add("observation", "Server 返回", result);
                String answer = model.chat(List.of(
                        Message.system("基于工具返回的事实回答，简洁准确。"),
                        Message.user(input + "\n工具结果: " + result)));
                trace.add("final", "生成回答", "工具与智能体已解耦：换 Server 实现，智能体不用改");
                return answer;
            }
            trace.add("final", "直接回答", "本次无需工具");
            return raw;
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }

    /**
     * 工具名片。
     *
     * @param name 工具名
     * @param description 用途
     * @param parameters 参数说明
     */
    record ToolDescriptor(String name, String description, String parameters) {
    }

    interface McpServer {
        List<ToolDescriptor> tools();

        String call(String toolName, String argsJson);
    }

    /**
     * 协议客户端，只暴露发现和调用两个方法。
     *
     * @param server 对端工具宿主
     */
    record McpClient(McpServer server) {
        List<Map<String, String>> listTools() {
            return server.tools().stream().map(tool -> {
                Map<String, String> card = new LinkedHashMap<>();
                card.put("name", tool.name());
                card.put("description", tool.description());
                card.put("parameters", tool.parameters());
                return card;
            }).toList();
        }

        String callTool(String name, String argsJson) {
            boolean known = server.tools().stream().anyMatch(tool -> tool.name().equals(name));
            if (!known) {
                return "MCP 错误: 工具不存在 " + name;
            }
            return server.call(name, argsJson);
        }
    }

    final class WeatherTimeServer implements McpServer {
        @Override
        public List<ToolDescriptor> tools() {
            return List.of(
                    new ToolDescriptor("get_weather", "查询城市实时天气", "city"),
                    new ToolDescriptor("get_time", "获取当前时间", ""));
        }

        @Override
        public String call(String toolName, String argsJson) {
            Object args = Texts.parseLenient(argsJson == null ? "{}" : argsJson);
            return switch (toolName) {
                case "get_weather" -> CityWeather.query(Json.str(args, "city"));
                case "get_time" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                default -> "未知工具";
            };
        }
    }
}
