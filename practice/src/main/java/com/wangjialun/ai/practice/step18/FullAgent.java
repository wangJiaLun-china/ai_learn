package com.wangjialun.ai.practice.step18;

import com.wangjialun.ai.practice.kit.Arithmetic;
import com.wangjialun.ai.practice.kit.CityWeather;
import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step18 · 全流程。一次 chat 按顺序走：输入守卫、读记忆、ReAct、输出校验、回写记忆。
 */
public interface FullAgent extends Agent {

    int MAX_STEPS = 5;
    int MAX_INPUT_LEN = 300;
    int COMPRESS_THRESHOLD = 10;

    class Impl implements FullAgent {

        private static final String PROTOCOL = """
                你是综合智能体。每轮只输出一个 JSON：
                {"thought":"...","action":"工具名","action_input":"参数"}
                或 {"thought":"...","final":"最终答案"}。
                可用工具：get_weather(city)、calculator(expression)、get_time()。
                """;
        private static final Pattern SENSITIVE = Pattern.compile("hack|攻击|爆破|破解|密码", Pattern.CASE_INSENSITIVE);
        private static final Pattern NAME = Pattern.compile("我(?:叫|的名字(?:是|叫)?)\\s*(?!(?:什么|啥|谁))([\\u4e00-\\u9fa5A-Za-z0-9]{1,12})");
        private static final Pattern LIKE = Pattern.compile("我(?:喜欢|爱)\\s*([\\u4e00-\\u9fa5A-Za-z0-9·\\s]{1,12})");

        private final ChatModel model;
        /** 跨轮对话。 */
        private final List<Message> history = new ArrayList<>();
        /** 从用户原话抽出的画像，键如「名字」「喜好」。 */
        private final Map<String, String> profile = new ConcurrentHashMap<>();
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step18 · 全流程智能体（守卫+记忆+ReAct+工具+保险丝）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            if (input.length() > MAX_INPUT_LEN) {
                trace.add("guard", "输入守卫", "长度 " + input.length() + " 超过上限 " + MAX_INPUT_LEN);
                return "输入过长，请精简后重试。";
            }
            Matcher sensitive = SENSITIVE.matcher(input);
            if (sensitive.find()) {
                trace.add("guard", "输入守卫", "命中敏感意图「" + sensitive.group() + "」");
                return "该请求涉及不安全操作，已被输入守卫拦截。";
            }
            trace.add("guard", "输入守卫", "长度与安全检查通过");
            captureProfile(input);
            trace.add("memory", "读取记忆", "历史 " + history.size() + " 条；画像：" + (profile.isEmpty() ? "暂无" : profile.toString()));

            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(PROTOCOL + portrait()));
            messages.addAll(history);
            messages.add(Message.user(input));
            String answer = react(messages);
            answer = guardOutput(answer, input);
            history.add(Message.user(input));
            history.add(Message.assistant(answer));
            String memo = "本轮已写入记忆，现共 " + history.size() + " 条。";
            if (history.size() > COMPRESS_THRESHOLD) {
                int keep = Math.min(6, history.size());
                List<Message> kept = new ArrayList<>(history.subList(history.size() - keep, history.size()));
                history.clear();
                history.addAll(kept);
                memo += " 超过阈值后已压缩。";
            }
            trace.add("memory", "回写记忆", memo);
            return answer;
        }

        private String react(List<Message> messages) {
            for (int step = 1; step <= MAX_STEPS; step++) {
                String raw = Texts.stripFence(model.chat(messages));
                Object parsed = Texts.parseLenient(raw);
                if (!(parsed instanceof Map<?, ?> decision)) {
                    trace.add("final", "Final Answer", raw);
                    return raw;
                }
                trace.add("thought", "Thought " + step, Json.str(decision, "thought") == null ? "" : Json.str(decision, "thought"));
                String fin = Json.str(decision, "final");
                if (fin != null) {
                    trace.add("final", "Final Answer", fin);
                    return fin;
                }
                String action = Json.str(decision, "action");
                String actionInput = Json.str(decision, "action_input");
                trace.add("action", "Action " + step, action + "(" + actionInput + ")");
                String observation = tool(action, actionInput);
                trace.add("tool", "Tool · " + action, "工具执行完成");
                trace.add("observation", "Observation " + step, observation);
                messages.add(Message.assistant(raw));
                messages.add(Message.user("Observation: " + observation));
            }
            String stop = "已达最大步数 " + MAX_STEPS + "，保险丝熔断。";
            trace.add("guard", "保险丝", stop);
            return stop;
        }

        private String guardOutput(String answer, String input) {
            if (answer != null && SENSITIVE.matcher(answer).find()) {
                trace.add("guard", "输出守卫", "答案命中敏感词");
                return "该内容不宜展示。";
            }
            if ((input.contains("我叫什么") || input.contains("我是谁")) && profile.containsKey("名字")) {
                trace.add("memory", "画像兜底", "命中画像名字=" + profile.get("名字"));
                return "你叫 " + profile.get("名字") + "（来自画像记忆）。";
            }
            return answer;
        }

        private void captureProfile(String input) {
            Matcher name = NAME.matcher(input);
            if (name.find()) {
                profile.put("名字", name.group(1));
            }
            Matcher like = LIKE.matcher(input);
            if (like.find()) {
                profile.put("喜好", like.group(1).trim());
            }
        }

        /** 画像放进系统提示词。用 Map 的普通文本，避免把后续消息一起卷进模型看到的标记。 */
        private String portrait() {
            if (profile.isEmpty()) {
                return "";
            }
            return "\n已知用户画像：" + profile + "。";
        }

        private static String tool(String action, String input) {
            return switch (action == null ? "" : action) {
                case "get_weather" -> CityWeather.query(input);
                case "calculator" -> Arithmetic.evalExact(input);
                case "get_time" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                default -> "未知工具: " + action;
            };
        }

        @Override
        public void reset() {
            history.clear();
            profile.clear();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
