package com.wangjialun.ai.toyagentscope.agent.step22;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.Json;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Step22 · 事件溯源 —— JSONL 事件流 append-only + 回放投影重建状态。
 * <p>
 * 思想：不保存「当前状态」，只保存「发生过的事件」；状态 = 回放全部事件得到的投影。
 * 好处：可审计、可回放、崩溃后可完全恢复。
 * <p>
 * 框架版：
 * <ul>
 *   <li><b>写</b>：AgentScope 2.0 的一切都是类型化事件流（streamEvents）。我们在 Tracer 上挂一个监听器，
 *       把关键事件（用户消息、工具调用、回复、回合结束）追加写入 data/events/step22-events.jsonl；</li>
 *   <li><b>读</b>：每一轮都用<b>全新的 sessionId</b>（框架内存里没有任何历史），
 *       先回放 JSONL 投影出对话历史 List&lt;Msg&gt;，再连同新问题一起交给智能体 —— 记忆完全来自事件日志。</li>
 * </ul>
 * 说明：AgentScope 2.1 起内置 session event log 与回放；2.0.4 没有，这里用事件流自己实现，原理相同。
 */
public interface EventSourcedAgent extends Agent {

    Path LOG_FILE = Path.of("data", "events", "step22-events.jsonl");

    /** 让模型能「看看日志里有什么」。 */
    class EventLogTools {
        @Tool(name = "read_event_log", description = "读取事件日志的最近若干条事件（JSONL）", readOnly = true)
        public String read(@ToolParam(name = "limit", description = "条数，默认 10", required = false) Integer limit) {
            List<String> lines = EventStore.lines();
            int n = limit == null || limit <= 0 ? 10 : limit;
            return lines.isEmpty() ? "（事件日志为空）" : String.join("\n", lines.subList(Math.max(0, lines.size() - n), lines.size()));
        }
    }

    /** 只追加的事件存储。 */
    final class EventStore {
        private EventStore() {
        }

        static synchronized void append(String type, Map<String, Object> payload) {
            try {
                Files.createDirectories(LOG_FILE.getParent());
                Map<String, Object> ev = new LinkedHashMap<>();
                ev.put("seq", lines().size() + 1);
                ev.put("ts", Instant.now().toString());
                ev.put("type", type);
                ev.put("payload", payload);
                Files.writeString(LOG_FILE, Json.write(ev) + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new IllegalStateException("写事件日志失败", e);
            }
        }

        static synchronized List<String> lines() {
            try {
                return Files.exists(LOG_FILE) ? Files.readAllLines(LOG_FILE, StandardCharsets.UTF_8) : List.of();
            } catch (IOException e) {
                return List.of();
            }
        }

        /** 回放投影：只关心用户消息与智能体回复，重建出对话历史。 */
        static List<Msg> replay() {
            List<Msg> history = new ArrayList<>();
            for (String line : lines()) {
                Map<String, Object> ev = Json.readMap(line);
                Map<?, ?> p = (Map<?, ?>) ev.get("payload");
                switch (String.valueOf(ev.get("type"))) {
                    case "USER_MESSAGE_APPENDED" -> history.add(new UserMessage(Json.str(p, "text")));
                    case "AGENT_REPLY_APPENDED" -> history.add(new AssistantMessage(Json.str(p, "text")));
                    default -> {
                    }
                }
            }
            return history;
        }

        static synchronized void clear() throws IOException {
            Files.deleteIfExists(LOG_FILE);
        }
    }

    class Impl implements EventSourcedAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new EventLogTools());
            this.agent = ReActAgent.builder()
                    .name("event-sourced")
                    .sysPrompt("你是一个有记忆的助手，你的记忆全部来自事件日志回放（已作为对话历史提供）。只有用户明确要求查看日志时才调用 read_event_log。回答简洁。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(3)
                    .build();
        }

        @Override
        public String name() {
            return "Step22 · 事件溯源（JSONL 追加 + 回放重建）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            // 1. 回放：从事件日志投影出历史（框架内存里什么都没有）
            List<Msg> history = EventStore.replay();
            log.add("thought", "回放事件日志", LOG_FILE + " 共 " + EventStore.lines().size() + " 条事件 → 投影出 " + history.size() + " 条历史消息");
            if (EventStore.lines().isEmpty()) {
                EventStore.append("SESSION_STARTED", Map.of("note", "首次对话"));
            }

            // 2. 先记事件，再执行
            EventStore.append("USER_MESSAGE_APPENDED", Map.of("text", input));
            List<Msg> msgs = new ArrayList<>(history);
            msgs.add(new UserMessage(input));
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId("replay-" + UUID.randomUUID()).build();
            Tracer.Result r = Tracer.of(log)
                    .listen(e -> {
                        if (e.getType() == AgentEventType.TOOL_CALL_END) {
                            EventStore.append("TOOL_CALLED", Map.of("tool", ((ToolCallEndEvent) e).getToolCallName()));
                        }
                    })
                    .run(agent.streamEvents(msgs, ctx));

            // 3. 回复与回合结束也写成事件
            EventStore.append("AGENT_REPLY_APPENDED", Map.of("text", r.text()));
            EventStore.append("TURN_COMPLETED", Map.of("reason", String.valueOf(r.reason())));
            log.add("memory", "追加事件", "USER_MESSAGE_APPENDED / AGENT_REPLY_APPENDED / TURN_COMPLETED → 日志共 "
                    + EventStore.lines().size() + " 条；最近：" + Texts.cut(EventStore.lines().stream()
                    .skip(Math.max(0, EventStore.lines().size() - 1)).collect(Collectors.joining()), 160));
            return r.text();
        }

        @Override
        public void reset() {
            try {
                EventStore.clear();
            } catch (IOException ignored) {
                // 尽力而为
            }
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
