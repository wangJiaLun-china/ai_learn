---
step: '09'
---

# 09 MCP

> 分组：工具的进化

**MCP：把工具标准化，让生态流动起来** —— 工具不再写死在智能体里。统一注册、动态发现、标准调用 —— 智能体世界的 USB-C 接口。

## 核心概念 {#concept}

### Step04 的遗留问题

工具是「编译期写死」的：智能体代码里硬编码了 `get_weather`。想换天气供应商？改代码重新部署。想接 100 个工具？智能体变成上帝类。

MCP（Model Context Protocol）的解法：**把工具拆成独立服务（Server），智能体作为 Client 通过标准协议使用它们**。

### 解耦带来什么

智能体 (Client) → 标准协议 → 工具服务 (Server)

- **工具热插拔**：Server 换实现（Mock 换真实天气 API），智能体零改动；
- **生态复用**：一个合规的 MCP Server 可以被所有智能体接入，写一次到处用；
- **权限收敛**：执行都在 Server 侧，Client 只拿得到协议允许的能力。

### 教学版 vs 真实 MCP

|   | 本场景 | 真实 MCP |
| --- | --- | --- |
| 通信 | 进程内 Java 接口 | JSON-RPC 2.0（stdio / Streamable HTTP） |
| Server 形态 | 嵌套接口模拟 | 独立进程 / 远程服务 |
| 核心方法 | 完全一致：tools/list + tools/call |   |

::: tip 💡 面试考点
「MCP 解决了什么问题？」—— M×N 集成问题。没有 MCP 时 M 个智能体对接 N 个工具要写 M×N 份胶水代码；有了 MCP 只需 M+N 份（各自实现协议一侧）。
:::

## 实现要点 {#impl}

### 协议只需要两个方法

```java
// MCP Server：工具的标准化宿主
public interface McpServer {
    List<ToolDescriptor> tools();          // tools/list 工具发现
    String call(String tool, String args); // tools/call 工具调用
}

// 工具描述符：标准"名片"
record ToolDescriptor(String name,
                      String description,
                      String parameters) {}

// 智能体侧：动态发现，而不是硬编码
List<Map<String, String>> tools = client.listTools();
String result = client.callTool("get_weather",
        "{\"city\": \"杭州\"}");
```

## ToyAgent vs ToyAgentScope {#compare}

**MCP：真·跨进程的工具标准** —— 同一个 jar 里写一个官方 SDK 的 MCP Server，以 stdio 子进程拉起；智能体通过 tools/list、tools/call 使用它的工具。

McpClientBuilder 拉起子进程 → initialize 握手 → tools/list → registerMcpClient → 本地工具 → 模型调用 → tools/call

### 服务端（官方 MCP Java SDK）

```java
McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(json))
        .serverInfo("toy-weather-time", "1.0.0")
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        .tools(McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder().name("get_weather")
                        .inputSchema(json, "{...}")
                        .annotations(READ_ONLY)          // readOnlyHint = true
                        .build())
                .callHandler((ex, req) -> text(CommonTools.weather(arg(req.arguments(), "city"))))
                .build())
        .build();
```

### 客户端（AgentScope 内置）

```java
McpClientWrapper client = McpClientBuilder.create("toy-weather-time")
        .stdioTransport(javaBin, "-cp", classpath, WeatherTimeMcpServer.class.getName())
        .buildAsync().block();
toolkit.registerMcpClient(client).block();      // tools/list → 注册为本地工具
```

::: warning ⚠️ 实测踩坑：远端工具默认要审批
第一次跑时，模型调用 MCP 工具后直接停在 PERMISSION_ASKING。原因：AgentScope 的 `McpTool.checkPermissions` 对**未声明只读**的远端工具默认要求人工授权。正确做法是在 MCP Server 端给工具加注解 `ToolAnnotations(readOnlyHint=true)` —— 由工具提供方声明副作用，而不是客户端一刀切放行。
:::

::: tip 💡 stdout 是协议通道
stdio 传输下，MCP Server 绝不能向 stdout 打日志（本项目用 slf4j-simple，默认输出到 stderr）。
:::

## 对应教程 {#chapters}

- ch07b MCP
- ch11 MCP：工具的标准化接口
