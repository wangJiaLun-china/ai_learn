package com.wangjialun.ai.practice.step17;

/**
 * 一个回合为什么停。用封闭类型把正常结束、保险丝和异常分成三种，调用处必须分别处理。
 */
public sealed interface TurnEndReason {

    /**
     * 正常结束。
     *
     * @param summary 结束说明
     */
    record Completed(String summary) implements TurnEndReason {
    }

    /**
     * 步数保险丝熔断。
     *
     * @param steps 已执行步数
     */
    record MaxSteps(long steps) implements TurnEndReason {
    }

    /**
     * 异常结束。
     *
     * @param message 错误说明
     */
    record Error(String message) implements TurnEndReason {
    }
}
