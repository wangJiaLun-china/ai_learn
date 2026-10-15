package com.wangjialun.ai.toyagentscope.agent.step17;

import io.agentscope.core.message.GenerateReason;

/**
 * Step17 · 回合结束原因（TurnEndReason）—— 用封闭类型统一表达「循环为什么停」。
 * <p>
 * AgentScope 用 {@link GenerateReason} 枚举表达结束原因（MODEL_STOP / MAX_ITERATIONS /
 * PERMISSION_ASKING / TOOL_SUSPENDED / INTERRUPTED …），这里把它收敛成业务关心的三种结局，
 * 用 sealed interface + record 表达，编译器会对 switch 做穷尽检查。
 */
public sealed interface TurnEndReason {

    /** 正常完成：模型给出最终答案。 */
    record Completed(String summary) implements TurnEndReason { }

    /** 触达步数保险丝：单回合最多 maxIters 步，强制收敛。 */
    record MaxSteps(long steps) implements TurnEndReason { }

    /** 异常终止（或框架因其他原因暂停）。 */
    record Error(String message) implements TurnEndReason { }

    /** 框架结束原因 → 业务结束原因。 */
    static TurnEndReason from(GenerateReason reason, String text, int maxSteps) {
        if (reason == null) return new Error("无结束原因");
        return switch (reason) {
            case MODEL_STOP, STRUCTURED_OUTPUT -> new Completed(text);
            case MAX_ITERATIONS -> new MaxSteps(maxSteps);
            default -> new Error("框架结束原因: " + reason);
        };
    }
}
