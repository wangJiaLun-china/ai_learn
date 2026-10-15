package com.wangjialun.ai.practice.kit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次 chat 的执行轨迹。页面和练习 Runner 都靠 type / label / detail 三段看中间过程。
 */
public final class TraceLog {

    private final List<Map<String, Object>> events = new ArrayList<>();

    /** 清空上一轮轨迹，避免串到下一次 chat。 */
    public void clear() {
        events.clear();
    }

    /**
     * 追加一条轨迹。
     *
     * @param type 事件类型，如 thought、action、observation、guard
     * @param label 给人看的短标题
     * @param detail 这一步的具体内容
     */
    public void add(String type, String label, String detail) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("label", label);
        event.put("detail", detail == null ? "" : detail);
        events.add(event);
    }

    /** 返回不可变快照，供 Agent.trace() 交给调用方。 */
    public List<Map<String, Object>> snapshot() {
        return List.copyOf(events);
    }
}
