package com.wangjialun.ai.toyagentscope.agent.step14;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.card.WellKnownAgentCardResolver;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step14 · A2A 协作 —— Agent Card 名片发现 + 标准化跨代理调用（真·A2A 协议）。
 * <p>
 * 与 Step13 子代理的区别：子代理是「我造的、在我进程里」；A2A 的对端是<b>别人的</b>智能体，
 * 可能用别的框架、别的语言实现，双方只约定协议：
 * <ol>
 *   <li><b>发现</b>：GET {base}/.well-known/agent-card.json 拿到名片（名称、能力、调用地址）；</li>
 *   <li><b>协作</b>：{@link A2aAgent} 按名片的地址发 JSON-RPC（message/stream），对端返回 task 事件流。</li>
 * </ol>
 * 在 AgentScope 里，远端代理被包装成一个普通 Agent（A2aAgent），可以像本地智能体一样 call()。
 * 远端代理由 {@link A2AServerHost} 用 AgentScopeA2aServer 发布。
 */
public interface A2AAgent extends Agent {

    class A2ATools {

        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        @Tool(name = "a2a_discover", description = "发现可协作的外部代理：拉取每个代理的 Agent Card 名片", readOnly = true)
        public String discover() {
            StringBuilder sb = new StringBuilder();
            for (String name : A2AServerHost.AGENTS.keySet()) {
                String url = A2AServerHost.baseUrl(name) + "/.well-known/agent-card.json";
                try {
                    String card = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                            HttpResponse.BodyHandlers.ofString()).body();
                    sb.append("- ").append(name).append("：").append(card.length() > 300 ? card.substring(0, 300) + "…" : card).append('\n');
                } catch (Exception e) {
                    sb.append("- ").append(name).append("：名片拉取失败 ").append(e.getMessage()).append('\n');
                }
            }
            return sb.toString().trim();
        }

        @Tool(name = "a2a_send", description = "通过 A2A 协议把任务发给指定的外部代理并等待结果")
        public String send(@ToolParam(name = "agent", description = "代理名，如 translator / weather") String agent,
                           @ToolParam(name = "task", description = "交给该代理的任务描述") String task) {
            if (!A2AServerHost.AGENTS.containsKey(agent)) {
                return "未知代理: " + agent + "，可用代理：" + A2AServerHost.AGENTS.keySet();
            }
            A2aAgent remote = A2aAgent.builder()
                    .name(agent)
                    // 注意：SDK 用 URI.resolve 拼名片地址，base 须以 / 结尾、相对路径不能以 / 开头
                    .agentCardResolver(WellKnownAgentCardResolver.builder()
                            .baseUrl(A2AServerHost.baseUrl(agent) + "/")
                            .relativeCardPath(".well-known/agent-card.json")
                            .build())
                    .build();
            Msg reply = remote.call(new UserMessage(task)).block(Duration.ofSeconds(120));
            Object taskId = reply == null || reply.getMetadata() == null ? null : reply.getMetadata().get("taskId");
            return (taskId == null ? "" : "[task_id=" + taskId + "] ") + (reply == null ? "" : reply.getTextContent());
        }
    }

    class Impl implements A2AAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new A2ATools());
            this.agent = ReActAgent.builder()
                    .name("coordinator")
                    .sysPrompt("""
                            你是协作协调者，可以通过 A2A 协议与外部代理协作：
                            - 用户要求发现代理时调用 a2a_discover；
                            - 需要翻译交给 translator，需要天气/户外建议交给 weather，用 a2a_send；
                            把外部代理的结果整理后回复用户，并注明由哪个代理完成。
                            """)
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(4)
                    .build();
        }

        @Override
        public String name() {
            return "Step14 · A2A 协作（Agent Card + 跨代理调用）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            return Tracer.of(log).run(agent.streamEvents(List.of(new UserMessage(input)), ctx)).text();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
