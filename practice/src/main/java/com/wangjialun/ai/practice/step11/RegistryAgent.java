package com.wangjialun.ai.practice.step11;

import com.wangjialun.ai.practice.kit.Arithmetic;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Step11 · 工具注册表。模型看到的清单来自注册表；执行器只 lookup，不认识具体工具。
 */
public interface RegistryAgent extends Agent {

    class Impl implements RegistryAgent {

        private static final String PREFIX = """
                你可以调用工具。可用清单由注册表生成：
                """;

        private final ChatModel model;
        private final ToolRegistry registry = new ToolRegistry();
        /** 工具名到注销器，卸载时调用。 */
        private final Map<String, Runnable> disposers = new HashMap<>();
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
            installDefaults();
        }

        private void installDefaults() {
            disposers.put("get_weather", registry.register(new WeatherTool()));
            disposers.put("calculator", registry.register(new CalculatorTool()));
            disposers.put("get_time", registry.register(new TimeTool()));
        }

        @Override
        public String name() {
            return "Step11 · 工具注册表（ToolDefinition 协议）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            trace.add("tool", "注册表就绪", "当前 " + registry.size() + " 个工具，清单由注册表自动生成");
            if (input.contains("查看工具") || input.contains("工具清单")) {
                trace.add("prompt", "工具清单 promptCatalog()", registry.promptCatalog().trim());
                return "当前注册表中共有 " + registry.size() + " 个工具：\n" + registry.promptCatalog()
                        + "\n这份清单会放进系统提示词。注册即生效，卸载即消失。";
            }
            if (input.startsWith("卸载：") || input.startsWith("卸载:")) {
                String name = input.substring(3).trim();
                Runnable disposer = disposers.remove(name);
                if (disposer == null) {
                    return "工具 " + name + " 不在注册表中。当前清单：\n" + registry.promptCatalog();
                }
                disposer.run();
                trace.add("guard", "注销器执行 disposer.run()", "工具 " + name + " 已摘除，剩余 " + registry.size() + " 个");
                return "已卸载工具 " + name + "。\n当前清单：\n" + registry.promptCatalog();
            }

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(PREFIX + registry.promptCatalog()
                    + "\n需要工具时只输出 {\"tool\":\"工具名\",\"arguments\":{\"参数名\":\"值\"}}。不需要时直接回答。"));
            messages.add(Message.user(input));
            for (int round = 1; round <= 2; round++) {
                String raw = model.chat(messages).trim();
                Object decision = Texts.parseLenient(raw);
                if (decision instanceof Map<?, ?> call && Json.str(call, "tool") != null) {
                    String toolName = Json.str(call, "tool");
                    trace.add("action", "模型发起工具调用", toolName + " " + Json.write(call.get("arguments")));
                    String result = invoke(toolName, call.get("arguments"));
                    messages.add(Message.assistant(raw));
                    messages.add(Message.user("工具 " + toolName + " 执行结果: " + result));
                    continue;
                }
                trace.add("final", "最终回答", "模型基于注册表提供的工具完成决策");
                return raw;
            }
            return "已达最大轮数，循环终止。";
        }

        private String invoke(String toolName, Object arguments) {
            return registry.lookup(toolName).map(tool -> {
                try {
                    String result = tool.execute(asMap(arguments));
                    trace.add("observation", "注册表执行 " + toolName, result);
                    return result;
                } catch (Exception ex) {
                    return "工具执行失败: " + ex.getMessage();
                }
            }).orElseGet(() -> {
                String missing = "未知工具: " + toolName + "。可用清单：\n" + registry.promptCatalog();
                trace.add("guard", "注册表未命中", "只能调用注册表里存在的工具");
                return missing;
            });
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> asMap(Object arguments) {
            Map<String, Object> map = new HashMap<>();
            Object parsed = arguments instanceof String text ? Texts.parseLenient(text) : arguments;
            if (parsed instanceof Map<?, ?> raw) {
                raw.forEach((key, value) -> map.put(String.valueOf(key), value));
            }
            return map;
        }

        @Override
        public void reset() {
            registry.clear();
            disposers.clear();
            installDefaults();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }

    /**
     * 天气工具。
     */
    record WeatherTool() implements ToolDefinition {
        @Override public String name() { return "get_weather"; }
        @Override public String description() { return "查询指定城市的实时天气"; }
        @Override public Map<String, String> parameters() { return Map.of("city", "城市名"); }
        @Override public String execute(Map<String, Object> args) {
            return CityWeather.query(String.valueOf(args.getOrDefault("city", "")));
        }
    }

    /**
     * 计算工具。
     */
    record CalculatorTool() implements ToolDefinition {
        @Override public String name() { return "calculator"; }
        @Override public String description() { return "计算四则运算"; }
        @Override public Map<String, String> parameters() { return Map.of("expression", "算式"); }
        @Override public String execute(Map<String, Object> args) {
            return Arithmetic.evalExact(String.valueOf(args.getOrDefault("expression", "")));
        }
    }

    /**
     * 时间工具。
     */
    record TimeTool() implements ToolDefinition {
        @Override public String name() { return "get_time"; }
        @Override public String description() { return "获取当前时间"; }
        @Override public Map<String, String> parameters() { return Map.of(); }
        @Override public String execute(Map<String, Object> args) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
}
