package com.wangjialun.ai.toyagentscope.agent.step03;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step03 · ReAct 智能体 —— 思考 → 行动 → 观察，循环直到给出答案。
 * <p>
 * 手写版：约 100 行。自己约定 JSON 输出协议 {"thought","action","action_input"}、
 * 自己解析、自己 switch 分发工具、自己把 Observation 拼回消息、自己数步数。
 * <p>
 * 框架版：AgentScope 的 {@link io.agentscope.core.ReActAgent} 就是这个循环本身——
 * <ul>
 *   <li>工具：{@link Toolkit#registerTool(Object)} 扫描 @Tool 方法，自动生成 JSON Schema；</li>
 *   <li>协议：用模型原生的 Function Calling，不再需要「只输出 JSON」的提示词约定；</li>
 *   <li>循环：reasoning（调模型）→ acting（执行工具、结果写回上下文）→ 再 reasoning；</li>
 *   <li>保险丝：{@code maxIters(5)}，超限发出 EXCEED_MAX_ITERS 事件。</li>
 * </ul>
 * 我们要做的只剩一件事：订阅事件流，把每一步翻译成页面轨迹（{@link Tracer}）。
 */
public interface ReActAgent extends Agent {

    int MAX_STEPS = 5;

    class Impl implements ReActAgent {

        private final io.agentscope.core.ReActAgent agent;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new CommonTools());
            this.agent = io.agentscope.core.ReActAgent.builder()
                    .name("react")
                    .sysPrompt("你是一个会使用工具解决问题的助手。需要实时信息或计算时先调用工具，再根据工具结果作答。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(MAX_STEPS)
                    .build();
        }

        @Override
        public String name() {
            return "Step03 · ReAct 智能体（思考-行动-观察循环）";
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
