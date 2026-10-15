---
step: '11'
---

# 11 工具注册表

> 分组：工具的进化

**工具注册表：一个接口 = 一个工具** —— 把工具从写死的 switch 里解放出来：统一协议 + 注册表，注册即生效、注销即消失。

## 核心概念 {#concept}

### switch 的天花板

Step03/04 里加一个工具要改两处：系统提示词的清单 + 执行器的 switch 分支。工具多了以后，这两处必然失同步 —— 提示词说有的工具执行器没有，或者反过来。

::: tip 💡 解法：协议 + 注册表
参考 生产级 Agent 框架 的 ToolDefinition（70 行接口）+ ToolRegistry：**实现协议四件套，注册进注册表，模型就能"看见"**。提示词清单由注册表自动渲染，永远与实际可调用工具一致。
:::

### 试试这样玩

- 发「查看工具清单」—— 看注册表内容，它就是模型每次看到的清单；
- 发「卸载：get_time」—— 注销器执行，工具被摘除；
- 再问「现在几点了？」—— 模型查无此工具，只能如实说没有。

生产对照：生产级框架 里插件停止/卸载时，宿主正是靠注销器回收工具、系统提示词与 Hook 注册项 —— **插件贡献的每一样东西都能被完整收回**。MCP 协议（场景 09）则是这套协议的跨进程标准化版本。

## 实现要点 {#impl}

### 协议四件套

```java
public interface ToolDefinition {
    String name();                          // 模型调用标识
    String description();                   // 用途：模型选工具的唯一依据
    Map<String, String> parameters();       // 参数说明（生产中是 JSON Schema）
    String execute(Map<String, Object> args); // 执行手脚
    default boolean concurrencySafe() { return false; } // 可否并行（预留）
}

// 一个 record 就是一个工具
record WeatherTool() implements ToolDefinition {
    public String name() { return "get_weather"; }
    public String description() { return "查询指定城市的实时天气"; }
    public String execute(Map<String, Object> args) { ... }
}
```

### 注册表的三职责

| 职责 | 方法 | 关键设计 |
| --- | --- | --- |
| 注册 | `register(tool)` | 返回**注销器 Runnable** —— 持有它才能摘除工具，"注册-回收对称性" |
| 发现 | `lookup(name)` | 执行器只查表不认具体工具 |
| 清单生成 | `promptCatalog()` | 自动渲染成系统提示词工具清单，**注册即生效、注销即消失** |

```java
Runnable disposer = registry.register(new WeatherTool());
disposer.run();   // 注销：模型下一次对话就"看不见"这个工具了
```

## ToyAgent vs ToyAgentScope {#compare}

**工具注册表：Toolkit 就是注册表** —— 注册 registerTool、发现 getToolSchemas、热注销 removeTool —— 下一轮推理模型就看不到被卸载的工具。

```java
Toolkit toolkit = new Toolkit(ToolkitConfig.builder().allowToolDeletion(true).build());
toolkit.registerTool(new CommonTools());
ReActAgent agent = ReActAgent.builder().toolkit(toolkit)...build();

// 运行期：一定要操作 agent.getToolkit()
agent.getToolkit().getToolSchemas();          // 发现：名称 + 描述 + JSON Schema
agent.getToolkit().removeTool("get_time");    // 热注销
```

::: warning ⚠️ 实测踩坑：Agent 会复制 Toolkit
`ReActAgent.Builder.build()` 内部执行 `this.toolkit.copy()`。构建之后再对你手里的那个 toolkit 增删工具**不会**影响智能体 —— 运行期请用 `agent.getToolkit()`。另外删除默认被禁止，需要 `allowToolDeletion(true)`。
:::

## 对应教程 {#chapters}

- 生产级框架 · ToolDefinition / ToolRegistry
