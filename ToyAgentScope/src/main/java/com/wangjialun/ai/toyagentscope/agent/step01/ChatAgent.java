package com.wangjialun.ai.toyagentscope.agent.step01;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;

import java.util.List;
import java.util.UUID;

/**
 * Step01 · 对话智能体 —— 智能体的最小 MVP（AgentScope 版）。
 * <p>
 * 手写版：{@code model.chat(List.of(Message.user(input)))}，一行接通模型。
 * 框架版：用 {@link ReActAgent#builder()} 装配一个「不带工具、不带系统提示词」的智能体，
 * 再 {@code agent.call(...)} —— 同样只有一行核心逻辑。
 * <p>
 * 关键概念：AgentScope 2.0 的智能体本身无状态，会话状态按 (userId, sessionId) 存放；
 * 这里每次调用都换一个新的 sessionId，所以每轮对话互相独立（没有记忆）。
 */
public interface ChatAgent extends Agent {

    class Impl implements ChatAgent {

        private final ReActAgent agent;

        public Impl(Model model) {
            this.agent = ReActAgent.builder()
                    .name("chat")
                    .model(model)
                    .build();
        }

        @Override
        public String name() {
            return "Step01 · 对话智能体（最小 MVP）";
        }

        @Override
        public String chat(String input) {
            // 全部实现就这一行核心逻辑：每次新会话 → 无记忆
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            Msg reply = agent.call(List.of(new UserMessage(input)), ctx).block();
            return reply == null ? "" : reply.getTextContent();
        }
    }
}
