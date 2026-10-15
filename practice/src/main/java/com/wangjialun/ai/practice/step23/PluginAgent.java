package com.wangjialun.ai.practice.step23;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.kit.WorkFiles;
import com.wangjialun.ai.practice.step11.ToolDefinition;
import com.wangjialun.ai.practice.step11.ToolRegistry;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Step23 · 插件。安装时用独立 ClassLoader 加载插件，工具批量注册；卸载时按注销器收回。
 */
public interface PluginAgent extends Agent {

    interface AgentPlugin {
        /** 插件标识，卸载时用来找到对应注册项。 */
        String id();

        /** 插件版本。 */
        String version();

        default void onStart() {
        }

        default void onStop() {
        }

        List<ToolDefinition> tools();
    }

    class Impl implements PluginAgent {

        private static final Path PLUGIN_DIR = WorkFiles.resolve("plugins", "step23");
        private static final String ENTRY = "com.wangjialun.ai.practice.step23.PluginAgent$TimePlugin";
        private static final String[] PLUGIN_CLASSES = {
                "com/wangjialun/ai/practice/step23/PluginAgent$TimePlugin.class",
                "com/wangjialun/ai/practice/step23/PluginAgent$TimePlugin$UptimeTool.class",
                "com/wangjialun/ai/practice/step23/PluginAgent$TimePlugin$CoinTool.class"
        };

        /**
         * 一次安装留下的资源。
         *
         * @param plugin 插件实例
         * @param classLoader 隔离加载器，卸载时要关闭
         * @param toolNames 这批工具的名字
         * @param disposers 注册时拿到的注销器
         */
        private record Loaded(AgentPlugin plugin, URLClassLoader classLoader, List<String> toolNames, List<Runnable> disposers) {
        }

        private final ChatModel model;
        private final ToolRegistry registry = new ToolRegistry();
        private final Map<String, Loaded> installed = new LinkedHashMap<>();
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step23 · 插件机制（隔离 ClassLoader 加载）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            trace.add("tool", "注册表就绪", "已安装 " + installed.size() + " 个插件，宿主本身不带工具");
            if (input.contains("安装插件") || input.contains("装载插件")) {
                return install();
            }
            if (input.contains("卸载插件") || input.contains("移除插件")) {
                return uninstall();
            }
            if (input.contains("查看工具") || input.contains("工具清单")) {
                trace.add("prompt", "工具清单", registry.isEmpty() ? "（注册表为空）" : registry.promptCatalog().trim());
                return "已安装插件：" + (installed.isEmpty() ? "（无）" : String.join("、", installed.keySet()))
                        + "\n当前注册表共 " + registry.size() + " 个工具：\n"
                        + (registry.isEmpty() ? "（空）" : registry.promptCatalog());
            }
            return decide(input);
        }

        private String decide(String input) {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("""
                    你可以调用工具。清单：
                    """ + (registry.isEmpty() ? "（当前没有任何工具）" : registry.promptCatalog()) + """
                    需要工具时只输出 {"tool":"工具名","arguments":{}}。不需要时直接回答。
                    """));
            messages.add(Message.user(input));
            for (int round = 1; round <= 2; round++) {
                String raw = model.chat(messages).trim();
                Object decision = Texts.parseLenient(raw);
                if (decision instanceof Map<?, ?> call && Json.str(call, "tool") != null) {
                    String toolName = Json.str(call, "tool");
                    trace.add("action", "模型发起工具调用", toolName);
                    String result = registry.lookup(toolName).map(tool -> {
                        try {
                            String output = tool.execute(Map.of());
                            trace.add("observation", "注册表执行 " + toolName, output);
                            return output;
                        } catch (Exception ex) {
                            return "工具执行失败: " + ex.getMessage();
                        }
                    }).orElseGet(() -> {
                        trace.add("guard", "注册表未命中", "插件未安装或已经卸载");
                        return "未知工具: " + toolName;
                    });
                    messages.add(Message.assistant(raw));
                    messages.add(Message.user("工具 " + toolName + " 执行结果: " + result));
                    continue;
                }
                trace.add("final", "最终回答", "模型基于插件工具完成决策");
                return raw;
            }
            return "已达最大轮数，循环终止。";
        }

        private String install() {
            if (!installed.isEmpty()) {
                return "已有插件在运行：" + String.join("、", installed.keySet()) + "。请先卸载。";
            }
            try {
                materialize();
                trace.add("tool", "插件包落盘", PLUGIN_DIR.toString());
                URLClassLoader loader = isolatedLoader();
                try {
                    Class<?> entry = Class.forName(ENTRY, true, loader);
                    AgentPlugin plugin = (AgentPlugin) entry.getDeclaredConstructor().newInstance();
                    plugin.onStart();
                    trace.add("action", "隔离加载", "入口 " + ENTRY + " 已实例化，onStart 已调用");
                    List<String> names = new ArrayList<>();
                    List<Runnable> disposers = new ArrayList<>();
                    for (ToolDefinition tool : plugin.tools()) {
                        disposers.add(registry.register(tool));
                        names.add(tool.name());
                    }
                    trace.add("observation", "工具桥接", names + " 已注册");
                    installed.put(plugin.id(), new Loaded(plugin, loader, names, disposers));
                    return "插件 " + plugin.id() + " v" + plugin.version() + " 安装成功，带来 "
                            + names.size() + " 个工具：\n" + registry.promptCatalog();
                } catch (Exception ex) {
                    loader.close();
                    return "插件加载失败：" + ex.getMessage();
                }
            } catch (IOException ex) {
                return "插件包落盘失败：" + ex.getMessage();
            }
        }

        private String uninstall() {
            if (installed.isEmpty()) {
                return "当前没有已安装的插件。";
            }
            StringBuilder names = new StringBuilder();
            installed.forEach((id, loaded) -> {
                loaded.disposers().forEach(Runnable::run);
                loaded.plugin().onStop();
                close(loaded.classLoader());
                trace.add("guard", "卸载 " + id, loaded.toolNames() + " 已注销，ClassLoader 已关闭");
                names.append(loaded.toolNames()).append(' ');
            });
            installed.clear();
            return "插件已卸载，工具 " + names + "已注销。注册表现在有 " + registry.size() + " 个工具。";
        }

        /** 子加载器优先加载插件类，契约接口仍走宿主加载器。 */
        private URLClassLoader isolatedLoader() throws IOException {
            URL url = PLUGIN_DIR.toUri().toURL();
            return new URLClassLoader(new URL[]{url}, PluginAgent.class.getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.contains("TimePlugin")) {
                        synchronized (getClassLoadingLock(name)) {
                            Class<?> loaded = findLoadedClass(name);
                            if (loaded == null) {
                                loaded = findClass(name);
                            }
                            if (resolve) {
                                resolveClass(loaded);
                            }
                            return loaded;
                        }
                    }
                    return super.loadClass(name, resolve);
                }
            };
        }

        private void materialize() throws IOException {
            Files.createDirectories(PLUGIN_DIR);
            for (String resource : PLUGIN_CLASSES) {
                try (InputStream in = PluginAgent.class.getResourceAsStream("/" + resource)) {
                    if (in == null) {
                        throw new IOException("类资源缺失: " + resource);
                    }
                    Path target = PLUGIN_DIR.resolve(resource);
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = Files.newOutputStream(target)) {
                        in.transferTo(out);
                    }
                }
            }
            Properties manifest = new Properties();
            manifest.setProperty("id", "time-plugin");
            manifest.setProperty("version", "1.0.0");
            manifest.setProperty("entrypoint", ENTRY);
            try (OutputStream out = Files.newOutputStream(PLUGIN_DIR.resolve("plugin.properties"))) {
                manifest.store(out, "plugin manifest");
            }
        }

        private static void close(URLClassLoader loader) {
            try {
                loader.close();
            } catch (IOException ignored) {
                // 关闭失败不阻止注销工具
            }
        }

        @Override
        public void reset() {
            installed.forEach((id, loaded) -> {
                loaded.disposers().forEach(Runnable::run);
                loaded.plugin().onStop();
                close(loaded.classLoader());
            });
            installed.clear();
            registry.clear();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }

    /** 演示插件：安装后带来运行时长和掷硬币两个工具。 */
    class TimePlugin implements AgentPlugin {
        private boolean started;

        @Override public String id() { return "time-plugin"; }
        @Override public String version() { return "1.0.0"; }
        @Override public void onStart() { started = true; }
        @Override public void onStop() { started = false; }

        @Override
        public List<ToolDefinition> tools() {
            return List.of(new UptimeTool(Instant.now(), started), new CoinTool());
        }

        /**
         * 运行时长工具。
         *
         * @param startedAt 插件启动时刻
         * @param active 插件是否仍处于启动状态
         */
        record UptimeTool(Instant startedAt, boolean active) implements ToolDefinition {
            @Override public String name() { return "uptime"; }
            @Override public String description() { return "查询宿主已运行时长"; }
            @Override public Map<String, String> parameters() { return Map.of(); }
            @Override public String execute(Map<String, Object> args) {
                long seconds = Duration.between(startedAt, Instant.now()).getSeconds();
                return "宿主已运行 " + seconds / 3600 + " 小时 " + (seconds % 3600) / 60 + " 分 " + seconds % 60 + " 秒";
            }
        }

        /** 掷硬币工具。 */
        record CoinTool() implements ToolDefinition {
            private static final Random RANDOM = new Random();
            @Override public String name() { return "coin_flip"; }
            @Override public String description() { return "掷一次硬币"; }
            @Override public Map<String, String> parameters() { return Map.of(); }
            @Override public String execute(Map<String, Object> args) {
                return RANDOM.nextBoolean() ? "正面" : "反面";
            }
        }
    }
}
