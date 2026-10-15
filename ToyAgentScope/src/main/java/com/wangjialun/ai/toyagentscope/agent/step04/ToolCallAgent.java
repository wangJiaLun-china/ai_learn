package com.wangjialun.ai.toyagentscope.agent.step04;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import io.agentscope.core.agent.accumulator.ReasoningContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.SystemMessage;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step04 · 工具调用 —— 把 Function Calling 拆开给你看。
 * <p>
 * Step03 的 ReActAgent 把「模型提议调用 → 执行工具 → 回填结果」藏在循环里了。
 * 这一步不用 Agent，直接用框架的三块积木手动走一遍：
 * <ol>
 *   <li>{@code toolkit.getToolSchemas()}：@Tool 方法自动生成的 JSON Schema，随请求发给模型；</li>
 *   <li>{@code model.stream(messages, schemas, options)}：模型返回 {@link ToolUseBlock}（name + 参数）；
 *       流式分片用框架的 {@link ReasoningContext} 累加成完整消息；</li>
 *   <li>{@code toolkit.callTool(...)}：按名字反射执行工具，得到 {@link ToolResultBlock}，
 *       作为 tool 角色消息回填，再让模型生成最终回答。</li>
 * </ol>
 * 这就是所有智能体框架「工具调用」的原始形态。
 */
public interface ToolCallAgent extends Agent {

    class Impl implements ToolCallAgent {

        private final Model model;
        private final Toolkit toolkit = new Toolkit();
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            this.model = model;
            toolkit.registerTool(new CommonTools());
        }

        @Override
        public String name() {
            return "Step04 · 工具调用智能体（Function Calling）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            List<Msg> messages = new ArrayList<>();
            messages.add(new SystemMessage("你是一个助手。需要天气、计算或时间信息时调用工具，否则直接回答。"));
            messages.add(new UserMessage(input));

            // 1. 带着工具 Schema 调模型
            Msg first = callModel(messages);
            List<ToolUseBlock> calls = first.getContentBlocks(ToolUseBlock.class);
            if (calls.isEmpty()) {
                // 模型认为不需要工具，直接作答
                log.add("final", "Final Answer（无需工具）", first.getTextContent());
                return first.getTextContent();
            }

            // 2. 执行模型提议的每个工具调用，结果以 tool 消息回填
            messages.add(first);
            for (ToolUseBlock call : calls) {
                log.add("tool", "Function Call", call.getName() + "(" + call.getInput() + ")");
                ToolResultBlock result = toolkit.callTool(ToolCallParam.builder()
                        .toolUseBlock(call)
                        .input(call.getInput())
                        .build()).block();
                String text = Texts.textOf(result);
                log.add("observation", "工具结果", text);
                messages.add(new ToolResultMessage(call.getId(), call.getName(), text));
            }

            // 3. 让模型基于工具结果生成最终回答
            Msg second = callModel(messages);
            log.add("final", "Final Answer", second.getTextContent());
            return second.getTextContent();
        }

        /** 一次模型调用：流式分片 → ReasoningContext 累加 → 完整的 assistant 消息。 */
        private Msg callModel(List<Msg> messages) {
            ReasoningContext acc = new ReasoningContext("tool-call");
            model.stream(messages, toolkit.getToolSchemas(), GenerateOptions.builder().build())
                    .doOnNext(acc::processChunk)
                    .blockLast();
            return acc.buildFinalMessage();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
