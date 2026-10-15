package com.wangjialun.ai.toyagentscope.agent.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * JSON 工具：直接复用 AgentScope 自带的 Jackson（手写版需要自己写 250 行 JSON 编解码）。
 */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> readMap(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }
    }

    public static String str(Map<?, ?> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
