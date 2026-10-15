package com.wangjialun.ai.practice.kit;

import java.util.Map;

/**
 * 教学用天气表。真实系统会换成外部 API，这里只为让工具调用有稳定观察结果。
 */
public final class CityWeather {

    /** 城市名到天气描述。 */
    private static final Map<String, String> TABLE = Map.of(
            "北京", "晴，26℃",
            "上海", "多云，28℃",
            "广州", "雷阵雨，31℃",
            "深圳", "阵雨，30℃",
            "杭州", "晴，27℃",
            "成都", "阴，22℃");

    private CityWeather() {
    }

    /** 从用户原话里挑出第一个已知城市，没有就默认北京。 */
    public static String cityIn(String text) {
        String source = text == null ? "" : text;
        for (String city : TABLE.keySet()) {
            if (source.contains(city)) {
                return city;
            }
        }
        return "北京";
    }

    /** 按城市名查天气；未收录时明确说不知道，不编造。 */
    public static String query(String city) {
        String key = city == null ? "" : city.trim();
        return TABLE.getOrDefault(key, "未收录城市 " + key + " 的数据");
    }
}
