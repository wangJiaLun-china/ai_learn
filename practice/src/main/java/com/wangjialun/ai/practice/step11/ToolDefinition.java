package com.wangjialun.ai.practice.step11;

import java.util.Map;

/**
 * 工具协议。注册表和执行器只认识这四个方法，不再写死 switch。
 */
public interface ToolDefinition {

    /** 模型输出的工具名。 */
    String name();

    /** 写进系统提示词的一句话用途。 */
    String description();

    /** 参数名到类型说明。 */
    Map<String, String> parameters();

    /** 真正执行，返回给模型看的观察文本。 */
    String execute(Map<String, Object> args) throws Exception;
}
