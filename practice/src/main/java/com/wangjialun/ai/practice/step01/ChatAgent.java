package com.wangjialun.ai.practice.step01;

import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Message;

import java.util.List;

/**
 * Step01 · 对话智能体。智能体的最小契约就是：一句话进去，一句话出来。
 */
public interface ChatAgent extends Agent {

    class Impl implements ChatAgent {

        /** 当前轮使用的模型，练习里默认是教材的 Mock。 */
        private final ChatModel model;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step01 · 对话智能体（最小 MVP）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            return model.chat(List.of(Message.user(input)));
        }

        @Override
        public List<java.util.Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
