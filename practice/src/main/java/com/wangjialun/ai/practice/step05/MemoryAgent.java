package com.wangjialun.ai.practice.step05;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step05 · 记忆。短期记忆就是每次请求都带上历史；超长时把最旧一轮压成摘要。
 */
public interface MemoryAgent extends Agent {

    /** 保留的对话轮数，一轮含一条用户消息和一条助手消息。 */
    int MAX_ROUNDS = 4;

    class Impl implements MemoryAgent {

        private static final String SYSTEM = """
                你是有记忆的聊天助手。结合历史摘要和新输入回答，用户说过的名字要保持一致。
                """;

        private final ChatModel model;
        /** 尚未被压缩的原始消息。 */
        private final List<Message> history = new ArrayList<>();
        /** 被滑出窗口的旧对话摘要。 */
        private String summary = "";
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step05 · 记忆系统（多轮记忆 + 上下文压缩）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            history.add(Message.user(input));
            if (history.size() > MAX_ROUNDS * 2) {
                foldOldestRound();
            }

            List<Message> context = new ArrayList<>();
            context.add(Message.system(SYSTEM));
            if (!summary.isEmpty()) {
                context.add(Message.system("【历史摘要】" + summary));
            }
            context.addAll(history);
            trace.add("memory", "记忆窗口",
                    "携带 " + history.size() + " 条历史消息" + (summary.isEmpty() ? "" : " + 1 条压缩摘要"));

            String answer = model.chat(context);
            history.add(Message.assistant(answer));
            trace.add("final", "回答完成",
                    "本条回答同时看到：系统提示词" + (summary.isEmpty() ? "" : " + 摘要") + " + 全部历史 + 新输入");
            return answer;
        }

        /** 把最旧的一问一答收成一句，腾出窗口。 */
        private void foldOldestRound() {
            Message user = history.remove(0);
            Message assistant = history.remove(0);
            String piece = Texts.clip(user.content(), 40) + "；随后助手回答了 " + Texts.clip(assistant.content(), 40);
            summary = summary.isEmpty() ? piece : summary + " → " + piece;
            trace.add("compress", "触发上下文压缩", "最旧 1 轮已摘要化，摘要长度 " + summary.length() + " 字符");
        }

        @Override
        public void reset() {
            history.clear();
            summary = "";
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
