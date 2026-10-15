package com.wangjialun.ai.toyagentscope.agent.step06;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step06 · 意图路由 —— 先判断「用户想干什么」，再分派给对应处理器。
 * <p>
 * 手写版：提示词要求模型「只输出 JSON」，再手动解析、兜底。
 * <p>
 * 框架版：<b>结构化输出</b>。{@code agent.call(msgs, Intent.class, ctx)} 让框架根据 Java record
 * 生成 JSON Schema（模型支持则走原生 response_format，否则自动退化为一个 generate_response 工具），
 * 返回值直接 {@code getStructuredData(Intent.class)} 拿到强类型对象 —— 不再手写 JSON 解析。
 * <p>
 * 路由表：weather → 天气工具；math → 计算器；chat → 闲聊智能体。
 */
public interface RouterAgent extends Agent {

    /** 意图分类结果（框架据此生成 JSON Schema）。 */
    record Intent(String intent, String reason) {
    }

    class Impl implements RouterAgent {

        private final ReActAgent classifier;
        private final ReActAgent chatter;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            this.classifier = ReActAgent.builder()
                    .name("router")
                    .sysPrompt("""
                            你是意图分类器。把用户输入分到以下之一：
                            - weather：询问天气
                            - math：需要算术计算
                            - chat：其他闲聊或知识问答
                            intent 只能取 weather / math / chat，reason 用一句话说明理由。
                            """)
                    .model(model)
                    .build();
            this.chatter = ReActAgent.builder()
                    .name("chatter")
                    .sysPrompt("你是一个幽默友好的聊天助手，回答简短。")
                    .model(model)
                    .build();
        }

        @Override
        public String name() {
            return "Step06 · 意图路由智能体（决策中枢）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            // 1. 结构化输出：意图分类
            Msg classified = classifier.call(List.of(new UserMessage(input)), Intent.class, newCtx()).block();
            Intent intent = Texts.structured(classified, Intent.class);
            String kind = intent == null || intent.intent() == null ? "chat" : intent.intent().trim().toLowerCase();
            log.add("intent", "意图识别: " + kind, intent == null ? "解析失败，按 chat 处理" : intent.reason());

            // 2. 路由分派
            String answer = switch (kind) {
                case "weather" -> {
                    String city = CommonTools.findCity(input);
                    city = city == null ? "北京" : city;
                    log.add("action", "路由 → 天气工具", "get_weather(" + city + ")");
                    String w = CommonTools.weather(city);
                    log.add("observation", "天气结果", w);
                    yield city + "今天" + w + "。";
                }
                case "math" -> {
                    String expr = extractExpression(input);
                    log.add("action", "路由 → 计算器", "calculator(" + expr + ")");
                    String v;
                    try {
                        v = CommonTools.format(CommonTools.Calc.eval(expr));
                    } catch (Exception e) {
                        v = "无法计算: " + e.getMessage();
                    }
                    log.add("observation", "计算结果", v);
                    yield expr + " = " + v;
                }
                default -> {
                    log.add("action", "路由 → 闲聊智能体", "chatter.call(...)");
                    Msg reply = chatter.call(List.of(new UserMessage(input)), newCtx()).block();
                    yield reply == null ? "" : reply.getTextContent();
                }
            };
            log.add("final", "Final Answer", answer);
            return answer;
        }

        private static String extractExpression(String input) {
            Matcher m = Pattern.compile("[\\d.+\\-*/×÷() ]{3,}").matcher(input);
            String best = "";
            while (m.find()) {
                if (m.group().trim().length() > best.length()) best = m.group().trim();
            }
            return best.isEmpty() ? input : best;
        }

        private static RuntimeContext newCtx() {
            return RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
