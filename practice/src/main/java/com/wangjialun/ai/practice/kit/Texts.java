package com.wangjialun.ai.practice.kit;

import com.wangjialun.ai.practice.llm.Json;

/**
 * 文本与模型输出的小工具：去掉代码围栏、解析 JSON、截断展示。
 */
public final class Texts {

    private Texts() {
    }

    /** 模型有时会用 ```json 包住协议文本，先剥掉再解析。 */
    public static String stripFence(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```\\w*\\s*", "").replaceFirst("```\\s*$", "").trim();
        }
        return s;
    }

    /** 解析失败时原样返回字符串，调用方用 instanceof Map 判断是不是协议对象。 */
    public static Object parseLenient(String raw) {
        try {
            return Json.parse(stripFence(raw));
        } catch (RuntimeException ex) {
            return raw;
        }
    }

    /** 把长文本收成一行，避免轨迹刷屏。 */
    public static String clip(String text, int limit) {
        String one = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return one.length() > limit ? one.substring(0, limit) + "…" : one;
    }
}
