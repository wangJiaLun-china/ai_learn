package com.wangjialun.ai.toyagentscope.agent.support;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 场景共用的小工具：文本相似度、敏感词、工具结果取文本等。
 */
public final class Texts {

    /** 守卫用敏感词表（Step15 / Step18 共用）。 */
    public static final List<String> SENSITIVE = List.of("密码", "攻击", "hack", "漏洞", "暴力", "炸药");

    private Texts() {
    }

    /** 命中的第一个敏感词，没有返回 null。 */
    public static String sensitiveHit(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase();
        return SENSITIVE.stream().filter(lower::contains).findFirst().orElse(null);
    }

    /** 字符二元组重合度：教学用的「向量相似度」替身。 */
    public static double bigramScore(String query, String doc) {
        Set<String> q = bigrams(query);
        if (q.isEmpty()) return 0;
        Set<String> d = bigrams(doc);
        long hit = q.stream().filter(d::contains).count();
        return (double) hit / q.size();
    }

    private static Set<String> bigrams(String s) {
        String t = s == null ? "" : s.toLowerCase().replaceAll("[\\s\\p{Punct}，。？！、：；“”‘’（）【】]", "");
        Set<String> set = new HashSet<>();
        for (int i = 0; i + 1 < t.length(); i++) set.add(t.substring(i, i + 2));
        return set;
    }

    /** 工具结果若是 JSON 字符串字面量（"晴，26℃"），去掉外层引号与转义。 */
    public static String unquote(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            try {
                return Json.MAPPER.readValue(s, String.class);
            } catch (Exception ignored) {
                // 不是合法 JSON 字符串，原样返回
            }
        }
        return s;
    }

    /** 取工具结果里的文本内容。 */
    public static String textOf(ToolResultBlock result) {
        if (result == null || result.getOutput() == null) return "";
        return unquote(result.getOutput().stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).getText())
                .collect(Collectors.joining()).trim());
    }

    /** 安全读取结构化输出：模型没按 Schema 返回时得到 null，由调用方兜底。 */
    public static <T> T structured(io.agentscope.core.message.Msg msg, Class<T> type) {
        if (msg == null || !msg.hasStructuredData()) return null;
        try {
            return msg.getStructuredData(type);
        } catch (Exception e) {
            return null;
        }
    }

    public static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
