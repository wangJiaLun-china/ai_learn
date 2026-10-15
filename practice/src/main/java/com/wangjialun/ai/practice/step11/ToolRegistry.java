package com.wangjialun.ai.practice.step11;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 工具目录。注册返回注销器，清单由当前目录实时渲染。
 */
public final class ToolRegistry {

    /** 工具名到实现，保持注册顺序。 */
    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    /** 放入目录，返回的 Runnable 被调用时摘掉同名工具。 */
    public synchronized Runnable register(ToolDefinition tool) {
        tools.put(tool.name(), tool);
        return () -> {
            synchronized (ToolRegistry.this) {
                tools.remove(tool.name());
            }
        };
    }

    public synchronized Optional<ToolDefinition> lookup(String name) {
        return Optional.ofNullable(name == null ? null : tools.get(name));
    }

    public synchronized int size() {
        return tools.size();
    }

    public synchronized boolean isEmpty() {
        return tools.isEmpty();
    }

    public synchronized void clear() {
        tools.clear();
    }

    /** 渲染成提示词里的工具清单。增删工具不用改提示词模板。 */
    public synchronized String promptCatalog() {
        StringBuilder sb = new StringBuilder();
        for (ToolDefinition tool : tools.values()) {
            sb.append("- ").append(tool.name()).append("(");
            int index = 0;
            for (Map.Entry<String, String> param : tool.parameters().entrySet()) {
                if (index++ > 0) {
                    sb.append(", ");
                }
                sb.append(param.getKey()).append(": ").append(param.getValue());
            }
            sb.append(") — ").append(tool.description()).append("\n");
        }
        return sb.toString();
    }
}
