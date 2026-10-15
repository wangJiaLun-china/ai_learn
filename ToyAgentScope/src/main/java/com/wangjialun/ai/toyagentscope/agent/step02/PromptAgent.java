package com.wangjialun.ai.toyagentscope.agent.step02;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step02 · 提示词工程 —— 系统提示词三层结构。
 * <p>
 * 手写版：把 system 消息手动塞到消息列表第一位。
 * 框架版：{@code ReActAgent.builder().sysPrompt(...)}，框架在每次推理时把它作为 system 消息注入
 * （还可以用 Middleware 的 onSystemPrompt 动态改写，见 Step24 / Step17）。
 * <p>
 * 三层结构：角色（你是谁）→ 背景（在什么场景）→ 任务与约束（要做什么、怎么输出）。
 */
public interface PromptAgent extends Agent {

    String SYSTEM_PROMPT = """
            【角色】你是「ToyAgentScope」教学助手，擅长用通俗类比解释 AI 智能体概念。
            【背景】用户正在学习如何用 AgentScope Java 2.0 构建智能体，基础薄弱，喜欢直白的例子。
            【任务与约束】
            1. 先用一句话给出结论，再用一个生活类比解释；
            2. 全文不超过 120 字，不使用 Markdown 标题；
            3. 不确定的内容直接说不知道，不编造。
            """;

    class Impl implements PromptAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            this.agent = ReActAgent.builder()
                    .name("prompt")
                    .sysPrompt(SYSTEM_PROMPT)
                    .model(model)
                    .build();
        }

        @Override
        public String name() {
            return "Step02 · 提示词工程（系统提示词三层结构）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            log.add("prompt", "System Prompt（sysPrompt 注入）", SYSTEM_PROMPT.trim());
            log.add("prompt", "User Prompt", input);
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            Msg reply = agent.call(List.of(new UserMessage(input)), ctx).block();
            String answer = reply == null ? "" : reply.getTextContent();
            log.add("final", "Final Answer", answer);
            return answer;
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
