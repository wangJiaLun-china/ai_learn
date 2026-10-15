package com.wangjialun.ai.toyagentscope.agent.step23;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Step23 · 插件机制 —— AgentPlugin 契约 + 隔离 ClassLoader 加载 + 工具组批量注册/注销。
 * <p>
 * AgentScope 2.0 没有通用插件系统（扩展点是 SPI、Skill、MCP），本场景用框架积木自己搭一个：
 * <ol>
 *   <li><b>契约</b>：{@link AgentPlugin}（id / onStart / tools / onStop）；</li>
 *   <li><b>安装</b>：把插件 .class 与 plugin.properties 放进 data/plugins/step23/；</li>
 *   <li><b>隔离加载</b>：{@link PluginClassLoader} 对插件包「子优先」加载，契约与框架类仍委托给宿主 ——
 *       所以插件类来自插件目录，而 @Tool 注解与宿主是同一个类，框架能识别；</li>
 *   <li><b>工具桥接</b>：插件的 @Tool 对象用 {@code toolkit.registration().tool(o).group("plugin_time")}
 *       注册进一个<b>工具组</b>；卸载时 {@code removeToolGroups} 一次性移除整组，再 onStop、关闭 ClassLoader。</li>
 *   <li><b>会话激活</b>：工具组是否对模型可见，是会话级状态（AgentState 的 toolContext.activatedGroups），
 *       调用前要为当前会话激活插件组。</li>
 * </ol>
 * 命令：「安装插件」「卸载插件」「查看工具清单」；其余输入交给模型（可调用插件工具）。
 */
public interface PluginAgent extends Agent {

    Path PLUGIN_DIR = Path.of("data", "plugins", "step23");
    String PLUGIN_PACKAGE = "com.wangjialun.ai.toyagentscope.agent.step23.plugin.";
    String ENTRYPOINT = PLUGIN_PACKAGE + "TimePlugin";
    String GROUP = "plugin_time";

    /** 插件契约：宿主只依赖这个接口。 */
    interface AgentPlugin {
        String id();

        void onStart();

        List<Object> tools();

        void onStop();
    }

    /** 子优先 ClassLoader：插件包内的类优先从插件目录加载，其余委托宿主。 */
    class PluginClassLoader extends URLClassLoader {

        PluginClassLoader(URL[] urls, ClassLoader parent) {
            super("plugin-loader", urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PLUGIN_PACKAGE)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) c = findClass(name);
                if (resolve) resolveClass(c);
                return c;
            }
        }
    }

    class Impl implements PluginAgent, AutoCloseable {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();
        private PluginClassLoader loader;
        private AgentPlugin plugin;

        public Impl(Model model) {
            this.agent = ReActAgent.builder()
                    .name("plugin-host")
                    .sysPrompt("你是插件宿主助手，只能使用当前已注册的工具；没有合适工具时直接说明「需要先安装插件」。")
                    .model(model)
                    .toolkit(new Toolkit(ToolkitConfig.builder().allowToolDeletion(true).build()))
                    .maxIters(3)
                    .build();
        }

        @Override
        public String name() {
            return "Step23 · 插件机制（隔离加载 + 批量注册/注销）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            Toolkit toolkit = agent.getToolkit();
            if (input.contains("安装插件") || input.contains("装载插件")) return install(toolkit);
            if (input.contains("卸载插件") || input.contains("移除插件")) return uninstall(toolkit);
            if (input.contains("查看工具") || input.contains("工具清单")) {
                String tools = toolkit.getToolNames().isEmpty() ? "（无工具）" : String.join(", ", toolkit.getToolNames());
                log.add("tool", "当前工具", tools);
                return "当前已注册工具：" + tools;
            }
            log.add("tool", "本轮可用工具", String.join(", ", toolkit.getToolNames()));
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            if (plugin != null) {
                // 关键：工具组的「激活」是会话级状态（AgentState.toolContext），新会话默认只看得到未分组的工具
                agent.getAgentState(ctx).getToolContext().addActivatedGroups(List.of(GROUP));
            }
            return Tracer.of(log).run(agent.streamEvents(List.of(new UserMessage(input)), ctx)).text();
        }

        private String install(Toolkit toolkit) {
            if (plugin != null) {
                log.add("guard", "重复安装", plugin.id() + " 已安装");
                return "插件 " + plugin.id() + " 已安装。";
            }
            try {
                // 1. 「发布」插件：把 .class 与描述文件放进插件目录
                for (String cls : List.of("TimePlugin", "TimePlugin$TimeTools")) {
                    String res = (PLUGIN_PACKAGE + cls).replace('.', '/') + ".class";
                    Path target = PLUGIN_DIR.resolve(res);
                    Files.createDirectories(target.getParent());
                    try (InputStream in = getClass().getClassLoader().getResourceAsStream(res)) {
                        if (in == null) throw new IOException("找不到插件类 " + res);
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Properties p = new Properties();
                p.setProperty("id", "time-plugin");
                p.setProperty("version", "1.0.0");
                p.setProperty("entrypoint", ENTRYPOINT);
                try (Writer w = Files.newBufferedWriter(PLUGIN_DIR.resolve("plugin.properties"), StandardCharsets.UTF_8)) {
                    p.store(w, "ToyAgentScope plugin descriptor");
                }
                log.add("action", "安装插件包", "写入 " + PLUGIN_DIR + "（2 个 .class + plugin.properties）");

                // 2. 隔离加载入口类
                loader = new PluginClassLoader(new URL[]{PLUGIN_DIR.toUri().toURL()}, getClass().getClassLoader());
                Class<?> entry = loader.loadClass(p.getProperty("entrypoint"));
                plugin = (AgentPlugin) entry.getDeclaredConstructor().newInstance();
                plugin.onStart();
                log.add("observation", "隔离加载", entry.getName() + " ← ClassLoader「" + entry.getClassLoader().getName()
                        + "」（宿主为「" + getClass().getClassLoader().getClass().getSimpleName() + "」）");

                // 3. 工具桥接：整组注册
                toolkit.createToolGroup(GROUP, "时间插件贡献的工具", true);
                for (Object tool : plugin.tools()) {
                    toolkit.registration().tool(tool).group(GROUP).apply();
                }
                log.add("tool", "批量注册工具组 " + GROUP, String.join(", ", toolkit.getToolNames()));
                log.add("final", "Final Answer", "插件 time-plugin 安装完成");
                return "✅ 插件 time-plugin 已安装，新增工具：" + String.join(", ", toolkit.getToolNames());
            } catch (Exception e) {
                log.add("guard", "安装失败", e.toString());
                return "插件安装失败：" + e.getMessage();
            }
        }

        private String uninstall(Toolkit toolkit) {
            if (plugin == null) {
                log.add("guard", "未安装", "没有已安装的插件");
                return "当前没有已安装的插件。";
            }
            toolkit.removeToolGroups(List.of(GROUP));
            plugin.onStop();
            log.add("action", "卸载插件", "removeToolGroups([" + GROUP + "]) → onStop() → 关闭 ClassLoader");
            closeLoader();
            plugin = null;
            log.add("tool", "剩余工具", toolkit.getToolNames().isEmpty() ? "（无工具）" : String.join(", ", toolkit.getToolNames()));
            return "🗑️ 插件已卸载，相关工具已整组注销。";
        }

        private void closeLoader() {
            if (loader != null) {
                try {
                    loader.close();
                } catch (IOException ignored) {
                    // 尽力而为
                }
                loader = null;
            }
        }

        @Override
        public void reset() {
            if (plugin != null) uninstall(agent.getToolkit());
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }

        @Override
        public void close() {
            closeLoader();
        }
    }
}
