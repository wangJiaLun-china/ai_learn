package com.wangjialun.ai.toyagentscope.agent.support;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 公共工具集：天气 / 计算器 / 时间。
 * <p>
 * 手写版里每个场景都要自己写「工具描述 + switch 分发 + 参数解析」；
 * AgentScope 只需在普通 Java 方法上标注 {@link Tool} / {@link ToolParam}，
 * 框架会自动生成 JSON Schema 交给模型做 Function Calling，并在模型发起调用时反射执行。
 */
public class CommonTools {

    private static final Map<String, String> WEATHER = Map.of(
            "北京", "晴，26℃", "上海", "多云，28℃", "广州", "雷阵雨，31℃",
            "深圳", "阵雨，30℃", "杭州", "晴，27℃", "成都", "阴，22℃");

    @Tool(name = "get_weather", description = "查询城市今天的天气（教学数据，收录北京/上海/广州/深圳/杭州/成都）", readOnly = true)
    public String getWeather(@ToolParam(name = "city", description = "城市名，如 北京") String city) {
        return weather(city);
    }

    @Tool(name = "calculator", description = "计算四则运算表达式，支持 + - * / 和括号，如 12*8、(3+5)/2", readOnly = true)
    public String calculator(@ToolParam(name = "expression", description = "算术表达式") String expression) {
        try {
            return format(Calc.eval(expression));
        } catch (Exception e) {
            return "计算失败: " + e.getMessage();
        }
    }

    @Tool(name = "get_time", description = "获取当前日期与时间", readOnly = true)
    public String getTime() {
        return now();
    }

    // ------------------------------------------------------------ 供场景直接调用的静态版本

    public static String weather(String city) {
        String key = city == null ? "" : city.trim().replace("市", "");
        return WEATHER.getOrDefault(key, "未收录城市 " + key + " 的数据");
    }

    public static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 从一句话里找出收录的城市名，找不到返回 null。 */
    public static String findCity(String text) {
        if (text == null) return null;
        for (String c : WEATHER.keySet()) {
            if (text.contains(c)) return c;
        }
        return null;
    }

    public static String format(double v) {
        return v == Math.rint(v) && !Double.isInfinite(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** 教学用递归下降求值器：expr := term (('+'|'-') term)* ; term := factor (('*'|'/') factor)* 。 */
    public static final class Calc {
        private final String s;
        private int pos;

        private Calc(String s) {
            this.s = s.replaceAll("\\s+", "").replace('×', '*').replace('÷', '/');
        }

        public static double eval(String expr) {
            if (expr == null || expr.isBlank()) throw new IllegalArgumentException("表达式为空");
            Calc c = new Calc(expr);
            double v = c.expr();
            if (c.pos != c.s.length()) throw new IllegalArgumentException("无法解析: " + expr);
            return v;
        }

        private double expr() {
            double v = term();
            while (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                char op = s.charAt(pos++);
                double r = term();
                v = op == '+' ? v + r : v - r;
            }
            return v;
        }

        private double term() {
            double v = factor();
            while (pos < s.length() && (s.charAt(pos) == '*' || s.charAt(pos) == '/')) {
                char op = s.charAt(pos++);
                double r = factor();
                if (op == '/' && r == 0) throw new ArithmeticException("除数不能为 0");
                v = op == '*' ? v * r : v / r;
            }
            return v;
        }

        private double factor() {
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++;
                double v = expr();
                if (pos >= s.length() || s.charAt(pos) != ')') throw new IllegalArgumentException("缺少右括号");
                pos++;
                return v;
            }
            if (pos < s.length() && s.charAt(pos) == '-') {
                pos++;
                return -factor();
            }
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalArgumentException("期望数字，位置 " + start);
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
