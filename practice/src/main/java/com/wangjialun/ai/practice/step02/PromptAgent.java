package com.wangjialun.ai.practice.step02;

import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Message;

import java.util.List;

/**
 * Step02 · 提示词工程。和 Step01 的差别只有一条 system 消息：角色、背景、任务约束。
 */
public interface PromptAgent extends Agent {

    class Impl implements PromptAgent {

        /** 三层系统提示词：先定身份，再定读者，最后定输出规矩。 */
        static final String SYSTEM_PROMPT = """
                # 角色
                你是技术助理，负责把智能体原理讲清楚。

                # 背景
                读者会写 Java，刚开始学智能体。

                # 任务与约束
                1. 用简体中文，先给结论再解释。
                2. 用户要求「用一句话」时，只回答一句话。
                3. 不确定就直说，不要编造。
                """;

        private final ChatModel model;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step02 · 提示词工程（系统提示词三层结构）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            trace.add("prompt", "注入系统提示词", "角色 / 背景 / 任务，共 " + SYSTEM_PROMPT.length() + " 字符");
            String answer = model.chat(List.of(Message.system(SYSTEM_PROMPT), Message.user(input)));
            trace.add("prompt", "模型按人设作答", "输出风格由 system 消息约束");
            return answer;
        }

        @Override
        public List<java.util.Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
