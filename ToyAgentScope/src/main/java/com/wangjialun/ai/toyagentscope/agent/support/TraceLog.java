package com.wangjialun.ai.toyagentscope.agent.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面执行轨迹：一组 {type, label, detail}。
 * <p>
 * type 决定前端时间线的配色（thought / action / observation / tool / final / guard …），
 * 与手写版 ToyAgent 的约定保持一致，因此 web 页面无需改动即可渲染。
 */
public final class TraceLog {

    private final List<Map<String, Object>> entries = new ArrayList<>();

    public synchronized TraceLog add(String type, String label, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("label", label);
        m.put("detail", detail == null ? "" : detail);
        entries.add(m);
        return this;
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    public synchronized List<Map<String, Object>> snapshot() {
        return List.copyOf(entries);
    }
}
