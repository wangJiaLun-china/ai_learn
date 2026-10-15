package com.wangjialun.ai.practice.step13;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step13 · 子代理。spawn 是全新上下文，fork 会把父会话抄进子代理的系统提示词。
 */
public interface SubagentAgent extends Agent {

    class Impl implements SubagentAgent {

        private static final Map<String, String> CHILDREN = new LinkedHashMap<>();

        static {
            CHILDREN.put("researcher", "你是调研子代理。根据任务输出 3 条要点，不写代码。");
            CHILDREN.put("writer", "你是写作子代理。根据任务输出 50 字以内短文，不解释。");
        }

        private static final String PREFIX = """
                你是主智能体，可以把子任务委托给子代理。可用：researcher 调研，writer 写作。
                spawn 是全新实例，fork 继承父会话。
                需要委托时只输出 {"tool":"dispatch_subagent","name":"writer","mode":"spawn","task":"子任务"}。
                不委托就直接用自然语言回答。
                """;

        private final ChatModel model;
        /** 父会话原文，fork 时整段交给子代理。 */
        private final List<String> parentTranscript = new ArrayList<>();
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step13 · 子代理（spawn 独立上下文 / fork 继承上下文）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            parentTranscript.add("用户: " + input);
            trace.add("tool", "主代理就绪", "可派遣 " + CHILDREN.keySet() + "，父会话 " + parentTranscript.size() + " 条");

            List<Message> messages = new ArrayList<>();
            String parent = parentTranscript.isEmpty() ? "" : "\n【父会话上下文】\n" + String.join("\n", parentTranscript);
            messages.add(Message.system(PREFIX + parent));
            messages.add(Message.user(input));

            for (int round = 1; round <= 3; round++) {
                String raw = model.chat(messages).trim();
                Object decision = Texts.parseLenient(raw);
                if (decision instanceof Map<?, ?> call && Json.str(call, "tool") != null) {
                    String name = Json.str(call, "name");
                    String mode = Json.str(call, "mode");
                    String task = Json.str(call, "task");
                    trace.add("action", "主代理派遣子代理", "subagent=" + name + "  mode=" + mode + "  task=" + task);
                    String result = dispatch(name, mode, task);
                    trace.add("observation", "子代理返回", result);
                    messages.add(Message.assistant(raw));
                    messages.add(Message.user("子代理 " + name + "(" + mode + ") 执行结果: " + result));
                    continue;
                }
                trace.add("final", "最终回答", "主代理汇总子代理结果");
                parentTranscript.add("助手: " + raw);
                return raw;
            }
            return "已达最大轮数，循环终止。";
        }

        private String dispatch(String name, String mode, String task) {
            String specialty = CHILDREN.get(name);
            if (specialty == null) {
                return "没有叫 " + name + " 的子代理。可用：" + CHILDREN.keySet();
            }
            boolean fork = "fork".equalsIgnoreCase(mode);
            StringBuilder system = new StringBuilder("【子代理身份】").append(specialty);
            if (fork) {
                system.append("\n【继承的父会话上下文】\n").append(String.join("\n", parentTranscript));
            } else {
                system.append("\n（spawn 模式：全新实例，看不到父会话）");
            }
            String report = model.chat(List.of(Message.system(system.toString()), Message.user(task))).trim();
            trace.add("tool", "子代理运行（" + mode + "）",
                    name + " 收到任务「" + task + "」，上下文 " + (fork ? "继承父会话 " + parentTranscript.size() + " 条" : "全新（0 条历史）"));
            return report;
        }

        @Override
        public void reset() {
            parentTranscript.clear();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
