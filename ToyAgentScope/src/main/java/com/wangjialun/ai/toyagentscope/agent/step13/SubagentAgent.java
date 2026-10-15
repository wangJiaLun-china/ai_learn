package com.wangjialun.ai.toyagentscope.agent.step13;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Step13 · 子代理 —— 主代理动态派遣：spawn 全新上下文 / fork 继承父会话。
 * <p>
 * 手写版：主代理输出 {"tool":"dispatch_subagent","mode":"spawn|fork"}，Java 里手动造子代理。
 * <p>
 * 框架版：
 * <ul>
 *   <li><b>spawn</b>：AgentScope 的「智能体即工具」——
 *       {@code toolkit.registration().subAgent(provider, SubAgentConfig)} 把一个 ReActAgent 注册成工具
 *       （researcher / writer）。子代理拿到的只有主代理写给它的 message，<b>看不到</b>父会话；</li>
 *   <li><b>fork</b>：框架没有现成 fork，我们用一个普通 @Tool 补上 —— 读出父代理的会话上下文
 *       {@code getAgentState(ctx).getContext()}，作为背景注入子代理的系统提示词，子代理因此「继承记忆」。</li>
 * </ul>
 * 对比实验：先「记住：我最喜欢紫色」，再分别 fork / spawn 子代理写贺词，看谁知道你喜欢紫色。
 */
public interface SubagentAgent extends Agent {

    class Impl implements SubagentAgent {

        private final Model model;
        private final ReActAgent parent;
        private final TraceLog log = new TraceLog();
        private RuntimeContext ctx = newCtx();

        public Impl(Model model) {
            this.model = model;
            Toolkit toolkit = new Toolkit();
            // spawn：子代理作为工具注册，每次调用都是全新上下文
            toolkit.registration()
                    .subAgent(() -> child("researcher", "你是研究员子代理，针对任务给出 3 条要点，简洁准确。", null),
                            SubAgentConfig.builder().toolName("spawn_researcher")
                                    .description("spawn 一个研究员子代理（全新上下文，看不到当前对话）调研某个主题").forwardEvents(false).build())
                    .apply();
            toolkit.registration()
                    .subAgent(() -> child("writer", "你是写作者子代理，按任务写一段简短文字。", null),
                            SubAgentConfig.builder().toolName("spawn_writer")
                                    .description("spawn 一个写作者子代理（全新上下文，看不到当前对话）写一段文字").forwardEvents(false).build())
                    .apply();
            // fork：继承父会话
            toolkit.registerTool(new ForkTool());
            this.parent = ReActAgent.builder()
                    .name("main")
                    .sysPrompt("""
                            你是主代理，可以派遣子代理完成任务：
                            - 用户说 spawn / 研究员 / 写作者时，调用 spawn_researcher 或 spawn_writer（子代理看不到对话历史）；
                            - 用户说 fork 时，调用 fork_subagent（子代理继承当前对话历史）；
                            - 用户只是陈述信息（如「记住…」）时直接简短确认，不要派遣子代理。
                            子代理返回后，把结果原样转述给用户，并说明用的是 spawn 还是 fork。
                            """)
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(4)
                    .build();
        }

        /** fork 工具：把父会话（只取用户/助手的文字往来）作为背景交给新子代理。 */
        public class ForkTool {

            @Tool(name = "fork_subagent", description = "fork 一个继承当前对话历史的子代理来完成任务")
            public String fork(@ToolParam(name = "task", description = "交给子代理的任务") String task) {
                String transcript = parent.getAgentState(ctx).getContext().stream()
                        .filter(m -> (m.getRole() == MsgRole.USER || m.getRole() == MsgRole.ASSISTANT)
                                && !m.hasContentBlocks(ToolUseBlock.class) && !m.hasContentBlocks(ToolResultBlock.class))
                        .map(m -> m.getRole().name().toLowerCase() + ": " + m.getTextContent())
                        .collect(Collectors.joining("\n"));
                ReActAgent forked = child("forked-writer", "你是 fork 出来的子代理，继承了父代理的对话历史：\n" + transcript, null);
                Msg reply = forked.call(List.of(new UserMessage(task)), newCtx()).block();
                return reply == null ? "" : reply.getTextContent();
            }
        }

        private ReActAgent child(String name, String sysPrompt, Toolkit toolkit) {
            ReActAgent.Builder b = ReActAgent.builder().name(name).sysPrompt(sysPrompt).model(model);
            if (toolkit != null) b.toolkit(toolkit);
            return b.build();
        }

        @Override
        public String name() {
            return "Step13 · 子代理（spawn / fork 动态派遣）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            log.add("tool", "子代理就绪", "spawn_researcher / spawn_writer（智能体即工具）· fork_subagent（继承父会话）");
            Set<String> subTools = Set.of("spawn_researcher", "spawn_writer", "fork_subagent");
            return Tracer.of(log)
                    .observationType(tool -> subTools.contains(tool) ? "tool" : "observation")
                    .run(parent.streamEvents(List.of(new UserMessage(input)), ctx))
                    .text();
        }

        private static RuntimeContext newCtx() {
            return RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
        }

        @Override
        public void reset() {
            parent.clearContext(ctx);
            ctx = newCtx();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
