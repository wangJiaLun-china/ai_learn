package com.wangjialun.ai.toyagentscope.agent.step05;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ConversationCompactor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step05 · 记忆智能体 —— 多轮记忆 + 上下文压缩。
 * <p>
 * 手写版：自己维护 history 列表，超过 4 轮就把最早的两条拼成「历史摘要」字符串。
 * <p>
 * 框架版：
 * <ul>
 *   <li><b>短期记忆</b>：AgentScope 2.0 没有 Memory 对象了，会话上下文存在 AgentState 里，
 *       按 (userId, sessionId) 隔离；同一个 sessionId 连续调用，自然就「记得」。</li>
 *   <li><b>上下文压缩</b>：用 {@link HarnessAgent} 自带的 compaction ——
 *       消息数达到 triggerMessages 时，框架调用模型把较早的消息总结成一条摘要，
 *       只保留最近 keepMessages 条原文（ConversationCompactor）。</li>
 * </ul>
 * HarnessAgent 是 ReActAgent 的「全家桶」封装（工作区、文件/Shell 工具、长期记忆、子代理…），
 * 本场景只想演示记忆，所以把其余能力都关掉。
 */
public interface MemoryAgent extends Agent {

    /** 上下文达到 8 条消息（约 4 轮）触发压缩，压缩后保留最近 4 条原文。 */
    int TRIGGER_MESSAGES = 8;
    int KEEP_MESSAGES = 4;

    /** 压缩用的摘要提示词，{messages} 由框架替换为待压缩的对话。 */
    String SUMMARY_PROMPT = """
            请用中文把下面的对话压缩成一段不超过 100 字的摘要，
            必须保留用户透露的个人信息（姓名、喜好、所在地等）和未完成的请求：

            {messages}
            """;

    class Impl implements MemoryAgent, AutoCloseable {

        private final HarnessAgent agent;
        private final TraceLog log = new TraceLog();
        private RuntimeContext ctx = newContext();

        public Impl(Model model) {
            this.agent = HarnessAgent.builder()
                    .name("memory")
                    .agentId("step05")
                    .sysPrompt("你是一个有记忆的助手，会记住用户在对话中提到的信息（如名字、喜好），回答简洁。")
                    .model(model)
                    .workspace(Path.of("data", "workspace", "step05"))
                    // 会话状态只放内存：HarnessAgent 默认会写到 ~/.agentscope/state/（disableSessionPersistence 管不到它）
                    .stateStore(new InMemoryAgentStateStore())
                    .compaction(CompactionConfig.builder()
                            .triggerMessages(TRIGGER_MESSAGES)
                            .keepMessages(KEEP_MESSAGES)
                            .flushBeforeCompact(false)
                            .offloadBeforeCompact(false)
                            .summaryPrompt(SUMMARY_PROMPT)
                            .build())
                    // 只保留记忆能力，关掉 harness 的其他内置能力
                    .disableFilesystemTools().disableShellTool().disableWebTools()
                    .disableMemoryTools().disableMemoryHooks()
                    .disableSubagents().disableToolsConfig().disableToolResultEviction()
                    .disableSessionPersistence().disableTranscript()
                    .disableWorkspaceContext().skillsEnabled(false)
                    .build();
        }

        @Override
        public String name() {
            return "Step05 · 记忆智能体（多轮记忆 + 上下文压缩）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            List<Msg> before = context();
            log.add("memory", "读取会话上下文", "sessionId=" + ctx.getSessionId() + "，已有 " + before.size() + " 条消息");

            Tracer.Result r = Tracer.of(log).noFinal().run(agent.streamEvents(List.of(new UserMessage(input)), ctx));

            List<Msg> after = context();
            boolean hadSummary = before.stream().anyMatch(Impl::isSummary);
            after.stream().filter(Impl::isSummary).findFirst().ifPresent(summary -> {
                if (!hadSummary || after.size() < before.size() + 2) {
                    log.add("compress", "上下文压缩（HarnessAgent compaction）",
                            "触发阈值 " + TRIGGER_MESSAGES + " 条，保留最近 " + KEEP_MESSAGES + " 条原文，其余总结为：\n"
                                    + Texts.cut(summary.getTextContent(), 400));
                }
            });
            log.add("memory", "写回会话上下文", "当前上下文 " + after.size() + " 条消息");
            log.add("final", "Final Answer", r.text());
            return r.text();
        }

        private List<Msg> context() {
            return agent.getDelegate().getAgentState(ctx).getContext();
        }

        private static boolean isSummary(Msg m) {
            return ConversationCompactor.SUMMARY_MSG_NAME.equals(m.getName());
        }

        private static RuntimeContext newContext() {
            return RuntimeContext.builder().userId("toy").sessionId("step05-" + UUID.randomUUID()).build();
        }

        @Override
        public void reset() {
            agent.clearContext(ctx);
            ctx = newContext();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }

        @Override
        public void close() {
            agent.close();
        }
    }
}
