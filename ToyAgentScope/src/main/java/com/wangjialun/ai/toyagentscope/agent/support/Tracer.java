package com.wangjialun.ai.toyagentscope.agent.support;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 事件流 → 页面轨迹 的翻译器（本项目最核心的胶水代码）。
 * <p>
 * 手写版 ToyAgent 在自己的 ReAct 循环里「顺手」记录轨迹；用了框架之后循环在框架内部，
 * 我们改为订阅 AgentScope 2.0 的类型化事件流 {@code streamEvents(...)}，把事件翻译成
 * 前端认识的 {type,label,detail}：
 * <pre>
 *   THINKING_BLOCK_*  / 工具调用前的文本  → thought
 *   TOOL_CALL_*                        → action      name(args)
 *   TOOL_RESULT_*                      → observation（可按工具名改成 retrieve / mcp / skill …）
 *   EXCEED_MAX_ITERS                   → guard       保险丝熔断
 *   REQUIRE_USER_CONFIRM / EXTERNAL    → guard       挂起等待人工
 *   AGENT_RESULT                       → final
 * </pre>
 */
public final class Tracer {

    /** 一次运行的结果：最终消息、最终文本、结束原因。 */
    public record Result(Msg msg, String text, GenerateReason reason) {

        public boolean is(GenerateReason r) {
            return reason == r;
        }
    }

    private final TraceLog log;
    private Function<String, String> actionType = name -> "action";
    private Function<String, String> observationType = name -> "observation";
    private boolean emitFinal = true;
    private Consumer<AgentEvent> listener = e -> {
    };

    private Tracer(TraceLog log) {
        this.log = log;
    }

    public static Tracer of(TraceLog log) {
        return new Tracer(log);
    }

    /** 按工具名决定「调用」节点的类型（默认 action）。 */
    public Tracer actionType(Function<String, String> f) {
        this.actionType = f;
        return this;
    }

    /** 按工具名决定「结果」节点的类型（默认 observation）。 */
    public Tracer observationType(Function<String, String> f) {
        this.observationType = f;
        return this;
    }

    /** 不自动追加 final 节点（由场景自己决定收尾）。 */
    public Tracer noFinal() {
        this.emitFinal = false;
        return this;
    }

    /** 旁路监听原始事件（如 Step22 把事件写入 JSONL）。 */
    public Tracer listen(Consumer<AgentEvent> l) {
        this.listener = l;
        return this;
    }

    /** 订阅事件流直到结束，边收边翻译成轨迹。 */
    public Result run(Flux<AgentEvent> events) {
        State s = new State();
        events.doOnNext(e -> {
            listener.accept(e);
            onEvent(s, e);
        }).blockLast();
        flushThinking(s);
        Msg msg = s.result;
        String text = msg == null ? s.text.toString().trim() : msg.getTextContent();
        GenerateReason reason = msg == null ? null : msg.getGenerateReason();
        if (emitFinal && text != null && !text.isBlank()
                && (reason == null || reason == GenerateReason.MODEL_STOP || reason == GenerateReason.STRUCTURED_OUTPUT)) {
            log.add("final", "Final Answer", text);
        }
        return new Result(msg, text == null ? "" : text, reason);
    }

    private void onEvent(State s, AgentEvent e) {
        switch (e.getType()) {
            case THINKING_BLOCK_DELTA -> s.thinking.append(((ThinkingBlockDeltaEvent) e).getDelta());
            case THINKING_BLOCK_END -> flushThinking(s);
            case TEXT_BLOCK_DELTA -> s.text.append(((TextBlockDeltaEvent) e).getDelta());
            case TOOL_CALL_START -> {
                flushThinking(s);
                // 工具调用之前模型说的话，就是它的「思考」
                if (!s.text.toString().isBlank()) {
                    log.add("thought", "Thought " + (s.step + 1), s.text.toString().trim());
                }
                s.text.setLength(0);
                ToolCallStartEvent ev = (ToolCallStartEvent) e;
                s.args.put(ev.getToolCallId(), new StringBuilder());
            }
            case TOOL_CALL_DELTA -> {
                ToolCallDeltaEvent ev = (ToolCallDeltaEvent) e;
                s.args.computeIfAbsent(ev.getToolCallId(), k -> new StringBuilder()).append(nz(ev.getDelta()));
            }
            case TOOL_CALL_END -> {
                ToolCallEndEvent ev = (ToolCallEndEvent) e;
                s.step++;
                s.callStep.put(ev.getToolCallId(), s.step);
                String args = s.args.getOrDefault(ev.getToolCallId(), new StringBuilder()).toString();
                String type = actionType.apply(ev.getToolCallName());
                log.add(type, "Action " + s.step, ev.getToolCallName() + "(" + compact(args) + ")");
            }
            case TOOL_RESULT_TEXT_DELTA -> {
                ToolResultTextDeltaEvent ev = (ToolResultTextDeltaEvent) e;
                s.results.computeIfAbsent(ev.getToolCallId(), k -> new StringBuilder()).append(nz(ev.getDelta()));
            }
            case TOOL_RESULT_END -> {
                ToolResultEndEvent ev = (ToolResultEndEvent) e;
                String out = Texts.unquote(s.results.getOrDefault(ev.getToolCallId(), new StringBuilder()).toString().trim());
                if (ev.getState() != null && ev.getState() != ToolResultState.SUCCESS) {
                    out = "[" + ev.getState() + "] " + out;
                }
                String type = observationType.apply(ev.getToolCallName());
                Integer n = s.callStep.get(ev.getToolCallId());
                log.add(type, n == null ? "Observation · " + ev.getToolCallName() : "Observation " + n, out);
            }
            case EXCEED_MAX_ITERS -> {
                ExceedMaxItersEvent ev = (ExceedMaxItersEvent) e;
                log.add("guard", "达到最大步数",
                        "maxIters=" + ev.getMaxIters() + "，框架熔断 ReAct 循环（保险丝）");
            }
            case REQUIRE_USER_CONFIRM -> log.add("guard", "等待人工审批",
                    "PERMISSION_ASKING：" + names(((RequireUserConfirmEvent) e).getToolCalls().stream()
                            .map(ToolUseBlock::getName).collect(Collectors.joining(", "))));
            case REQUIRE_EXTERNAL_EXECUTION -> log.add("guard", "挂起等待外部输入",
                    "TOOL_SUSPENDED：" + names(((RequireExternalExecutionEvent) e).getToolCalls().stream()
                            .map(ToolUseBlock::getName).collect(Collectors.joining(", "))));
            case AGENT_RESULT -> s.result = ((AgentResultEvent) e).getResult();
            default -> {
            }
        }
    }

    private void flushThinking(State s) {
        if (!s.thinking.toString().isBlank()) {
            log.add("thought", "Thinking", s.thinking.toString().trim());
        }
        s.thinking.setLength(0);
    }

    private static String names(String s) {
        return s == null || s.isBlank() ? "(无)" : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 把 {"city":"北京"} 压成 city=北京，轨迹更好读。 */
    private static String compact(String json) {
        String t = json.trim();
        if (t.startsWith("{")) {
            try {
                Map<String, Object> m = Json.readMap(t);
                return m.entrySet().stream().map(en -> en.getKey() + "=" + en.getValue())
                        .collect(Collectors.joining(", "));
            } catch (Exception ignored) {
                // 非完整 JSON，原样展示
            }
        }
        return t;
    }

    private static final class State {
        final StringBuilder thinking = new StringBuilder();
        final StringBuilder text = new StringBuilder();
        final Map<String, StringBuilder> args = new HashMap<>();
        final Map<String, StringBuilder> results = new HashMap<>();
        final Map<String, Integer> callStep = new HashMap<>();
        Msg result;
        int step;
    }
}
