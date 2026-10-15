package com.wangjialun.ai.practice.kit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只支持「数字 运算符 数字」的四则运算。智能体不心算，把算式交给这个执行器。
 */
public final class Arithmetic {

    private static final Pattern BINARY = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)");

    private Arithmetic() {
    }

    /** 从一段话里抽出第一处算式并求值；抽不到就说明原因。 */
    public static String evalFirst(String text) {
        Matcher matcher = BINARY.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return "无法解析表达式";
        }
        return eval(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    /** 整段就是一个算式时才成功，避免把句子里的数字误算。 */
    public static String evalExact(String expression) {
        Matcher matcher = BINARY.matcher(expression == null ? "" : expression);
        if (!matcher.matches()) {
            return "无法解析表达式";
        }
        return eval(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    private static String eval(String left, String operator, String right) {
        double a = Double.parseDouble(left);
        double b = Double.parseDouble(right);
        double value = switch (operator) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> {
                if (b == 0) {
                    yield Double.NaN;
                }
                yield a / b;
            }
            default -> Double.NaN;
        };
        if (Double.isNaN(value)) {
            return "无法计算";
        }
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
