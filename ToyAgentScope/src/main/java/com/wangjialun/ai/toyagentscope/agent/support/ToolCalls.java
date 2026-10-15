package com.wangjialun.ai.toyagentscope.agent.support;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;

import java.util.Map;
import java.util.UUID;

/**
 * 绕过模型、由代码直接调用 Toolkit 里的工具（工作流节点、确定性命令等场景使用）。
 * <p>
 * 要点：Toolkit 会按工具的 JSON Schema 校验参数，校验的是 ToolUseBlock 里的原始 JSON（content），
 * 所以手工构造调用时 input（Map）和 content（JSON 字符串）都要填。
 */
public final class ToolCalls {

    private ToolCalls() {
    }

    public static ToolResultBlock call(Toolkit toolkit, String name, Map<String, Object> args) {
        ToolUseBlock use = ToolUseBlock.builder()
                .id("direct-" + UUID.randomUUID())
                .name(name)
                .input(args)
                .content(Json.write(args))
                .build();
        return toolkit.callTool(ToolCallParam.builder().toolUseBlock(use).input(args).build()).block();
    }

    public static String callText(Toolkit toolkit, String name, Map<String, Object> args) {
        return Texts.textOf(call(toolkit, name, args));
    }
}
