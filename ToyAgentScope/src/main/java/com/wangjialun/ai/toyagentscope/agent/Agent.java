package com.wangjialun.ai.toyagentscope.agent;

import java.util.List;
import java.util.Map;

/**
 * 智能体契约 —— 与手写版 ToyAgent 完全相同，只有一个核心方法：
 *
 * <pre>{@code
 *     String chat(String input);
 * }</pre>
 *
 * 区别在于方法内部：手写版自己实现 ReAct 循环、工具分发、记忆裁剪；
 * 本项目把这些交给 AgentScope Java 2.0（ReActAgent / Toolkit / Middleware / 权限系统…），
 * 每个场景只负责「装配框架组件」+「把框架事件流翻译成页面轨迹」。
 * <p>
 * trace() 把中间过程暴露给页面渲染（思考步骤、工具调用等），
 * reset() 供有状态场景（如记忆）清空上下文。
 */
public interface Agent {

    /** 场景名称，用于页面展示。 */
    String name();

    /** 智能体最小契约：输入用户消息，返回回答。 */
    String chat(String input);

    /** 最近一次 chat 的执行轨迹（供页面渲染时间线），无轨迹则返回空列表。 */
    default List<Map<String, Object>> trace() {
        return List.of();
    }

    /** 清空场景内部状态（如记忆），无状态场景无需实现。 */
    default void reset() {
    }
}
