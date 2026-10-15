---
step: '23'
---

# 23 插件

> 分组：扩展与形态

**插件机制：让能力即插即拔** —— Step11 解决「工具怎么注册」，本步解决「工具从哪来」—— 插件是一批工具的动态载体：安装即批量注册，卸载即批量注销。

## 核心概念 {#concept}

### 为什么需要插件

工具写死在宿主里，每加一个能力都要重新编译、重新部署。**插件机制把「能力」变成运行时可安装、可卸载的包**：第三方作者按契约实现一个接口，宿主加载后模型立刻「看见」新工具。

::: tip 💡 一句话理解
插件 = 工具的 JAR 包。生产级框架 的整个插件生态（安装 / 激活 / 运行 / 管理 / 查询五条流水线 + maven archetype 脚手架）都建立在这句话上。
:::

### 生产级框架 的四层设计

| 层次 | 生产级框架 组件 | 职责 |
| --- | --- | --- |
| 契约 | `JavaHarnessPlugin` + `PluginManifest` | onStart/onStop/tools() + META-INF/plugin.yaml 声明入口 |
| 加载 | `JavaPluginLoader` | 独立 URLClassLoader 隔离加载；entrypoint 优先，Java SPI 兜底 |
| 生命周期 | `PluginHotReloader` / `JavaPluginRuntimeManager` | 安装→激活→运行→卸载，热插拔 |
| 桥接 | `PluginToolBridgeService` | 插件工具批量注册进 ToolRegistry，disposers 按 pluginId 归档 |

另有 `JsonRpcPluginToolBridge` 支持进程外插件（JSON-RPC 桥接），进程内 / 进程外双模式。

### 和 Step11 的分工

| 维度 | 15 · 工具注册表 | 21 · 插件机制 |
| --- | --- | --- |
| 回答的问题 | 工具怎么注册、怎么被发现 | 工具从哪来、怎么装进来、怎么卸掉 |
| 工具生命周期 | 单个工具级（注册 / 注销） | 插件级批量（整包安装 / 整包回收） |
| 类加载 | 同 ClassLoader | **独立 ClassLoader 隔离**，卸载即关闭 |

### 试试这样玩

- 「安装插件」—— 观察轨迹四步：落盘 → 隔离加载 → onStart → 批量注册；
- 「掷一次硬币」「宿主运行多久了」—— 模型自动看见并调用插件工具；
- 「卸载插件」→「再看看工具清单」—— 工具消失，再掷硬币会命中「注册表未命中」。

生产化路径：插件包换成真 JAR（maven archetype 一键生成骨架）、plugin.yaml 换 MANIFEST、加版本冲突校验与签名验证、双模式桥接（进程内 / JSON-RPC 进程外）。

## 实现要点 {#impl}

### 本场景的骨架

1. 释放插件包 → 2. 隔离加载 → 3. onStart + 收集工具 → 4. 批量注册

```java
// 1. 契约：插件作者唯一要实现的东西
public interface AgentPlugin {
    String id();  String version();
    default void onStart() {}
    default void onStop() {}
    List<ToolDefinition> tools();   // 插件带来的工具
}

// 2. 隔离加载：parent 指向宿主，插件类只从 plugins/ 目录来
URLClassLoader cl = new URLClassLoader(
    new URL[]{dir.toUri().toURL()}, PluginAgent.class.getClassLoader());
AgentPlugin plugin = (AgentPlugin) Class
    .forName(entrypoint, true, cl)
    .getDeclaredConstructor().newInstance();
plugin.onStart();

// 3. 工具桥接：注册 + disposer 按 pluginId 归档（卸载即批量注销）
for (ToolDefinition tool : plugin.tools())
    disposers.add(registry.register(tool));

// 4. 卸载三步回收：批量 disposer → onStop → cl.close()
```

## ToyAgent vs ToyAgentScope {#compare}

**插件机制：隔离加载 + 工具组** —— AgentPlugin 契约 + 子优先 ClassLoader 从 data/plugins 加载插件类；插件的 @Tool 注册进一个工具组，卸载时整组移除。

```java
// 子优先：插件包内的类从插件目录加载，契约与框架类（含 @Tool 注解）仍委托宿主
protected Class<?> loadClass(String name, boolean resolve) {
    if (!name.startsWith(PLUGIN_PACKAGE)) return super.loadClass(name, resolve);
    Class<?> c = findLoadedClass(name);
    return c != null ? c : findClass(name);
}

// 工具桥接：整组注册 / 整组注销
toolkit.createToolGroup("plugin_time", "时间插件贡献的工具", true);
plugin.tools().forEach(t -> toolkit.registration().tool(t).group("plugin_time").apply());
...
toolkit.removeToolGroups(List.of("plugin_time"));   // 需 allowToolDeletion(true)
```

::: warning ⚠️ 实测踩坑：工具注册了，模型却看不到
工具组是否对模型可见是**会话级状态**：推理时框架用 `AgentState.toolContext.activatedGroups` 过滤工具，新会话默认只看得到未分组的工具。调用前要为当前会话激活：`agent.getAgentState(ctx).getToolContext().addActivatedGroups(List.of("plugin_time"))`。
:::

::: tip 💡 为什么要子优先
标准 URLClassLoader 是父优先：插件类如果也在宿主 classpath 上，会从宿主加载，「隔离」就是假的。轨迹里能看到插件类来自「plugin-loader」，而宿主是 AppClassLoader。
:::

## 对应教程 {#chapters}

- 生产级框架 · JavaPluginLoader / PluginToolBridgeService
