package com.wangjialun.ai.practice.step19;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step19 · 人工介入。缺信息时挂起，下一条用户消息当作答复，再带着「人工答复：」续跑。
 */
public interface AskAgent extends Agent {

    class Impl implements AskAgent {

        private static final String SYSTEM = """
                缺关键信息时不要猜，只输出 {"ask":"你的问题"}。
                信息完整时只输出 {"final":"最终答案"}。
                """;

        private final ChatModel model;
        private final List<Message> history = new ArrayList<>();
        /** 挂起中的问题；非空表示下一句是人工答复。 */
        private String pendingQuestion;
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step19 · 人工介入（ask_user_question：提问挂起 → 答复续跑）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            if (pendingQuestion != null) {
                String question = pendingQuestion;
                pendingQuestion = null;
                trace.add("observation", "人工答复回填", "问题「" + question + "」收到答复：" + input);
                history.add(Message.user("人工答复：" + input));
                String answer = resume(question);
                history.add(Message.assistant(answer));
                return answer;
            }
            history.add(Message.user(input));
            Object parsed = Texts.parseLenient(ask(SYSTEM));
            String question = Json.str(parsed, "ask");
            if (question != null) {
                pendingQuestion = question;
                trace.add("action", "调用 ask_user_question", "本轮挂起，等待人工答复");
                trace.add("guard", "会话挂起", "下一条消息将作为答复回填");
                return question + "\n\n（直接回复即可，我会带着你的答复继续。）";
            }
            String answer = finalOf(parsed, "");
            trace.add("final", "直接作答", "信息完整，无需人工介入");
            history.add(Message.assistant(answer));
            return answer;
        }

        private String resume(String question) {
            Object parsed = Texts.parseLenient(ask(SYSTEM + "\n此前你问过「" + question + "」，用户已在最后一条人工答复里回答。现在直接完成任务。"));
            trace.add("final", "turn 恢复 · 完成", "人工答复已注入上下文");
            return finalOf(parsed, "");
        }

        private String ask(String system) {
            List<Message> request = new ArrayList<>();
            request.add(Message.system(system));
            request.addAll(history);
            return model.chat(request).trim();
        }

        private static String finalOf(Object parsed, String fallback) {
            String fin = Json.str(parsed, "final");
            if (fin != null && !fin.isBlank()) {
                return fin;
            }
            return parsed instanceof String text && !text.isBlank() ? text : fallback;
        }

        @Override
        public void reset() {
            history.clear();
            pendingQuestion = null;
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
