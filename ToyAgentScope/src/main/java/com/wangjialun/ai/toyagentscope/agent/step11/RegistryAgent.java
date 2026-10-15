package com.wangjialun.ai.toyagentscope.agent.step11;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Step11 · 工具注册表 —— 注册 / 发现 / 热注销。
 * <p>
 * 手写版：自己定义 ToolDefinition 协议 + ToolRegistry，register() 返回 disposer 用于注销。
 * <p>
 * 框架版：{@link Toolkit} 就是注册表 ——
 * <ul>
 *   <li>注册：{@code registerTool(obj)} 扫描 @Tool 方法；</li>
 *   <li>发现：{@code getToolSchemas()} 拿到每个工具的名称、描述、JSON Schema（也就是发给模型的内容）；</li>
 *   <li>热注销：{@code removeTool(name)}（需 ToolkitConfig.allowToolDeletion(true)），
 *       下一次推理模型就看不到这个工具了，无需重建智能体。</li>
 * </ul>
 * 注意：ReActAgent 构建时会复制一份 Toolkit，所以运行期要操作 {@code agent.getToolkit()}。
 * <p>
 * 命令：「查看工具 / 工具清单」列出工具；「卸载：工具名」热注销；reset 恢复默认工具。
 */
public interface RegistryAgent extends Agent {

    class Impl implements RegistryAgent {

        private final Model model;
        private final TraceLog log = new TraceLog();
        private ReActAgent agent;

        public Impl(Model model) {
            this.model = model;
            this.agent = build();
        }

        private ReActAgent build() {
            Toolkit toolkit = new Toolkit(ToolkitConfig.builder().allowToolDeletion(true).build());
            toolkit.registerTool(new CommonTools());
            return ReActAgent.builder()
                    .name("registry")
                    .sysPrompt("你是一个助手，只能使用当前注册的工具。如果没有合适的工具，直接说明「该工具已被卸载，无法完成」。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(3)
                    .build();
        }

        @Override
        public String name() {
            return "Step11 · 工具注册表智能体（注册/发现/热注销）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            Toolkit toolkit = agent.getToolkit();
            String text = input.trim();

            if (text.contains("查看工具") || text.contains("工具清单")) {
                String catalog = catalog(toolkit);
                log.add("tool", "工具发现 getToolSchemas()", catalog);
                return "当前注册的工具：\n" + catalog;
            }

            if (text.startsWith("卸载：") || text.startsWith("卸载:")) {
                String toolName = text.substring(3).trim();
                if (!toolkit.getToolNames().contains(toolName)) {
                    log.add("guard", "卸载失败", "工具不存在: " + toolName);
                    return "没有名为 " + toolName + " 的工具。当前工具：" + toolkit.getToolNames();
                }
                toolkit.removeTool(toolName);
                log.add("guard", "热注销 removeTool(\"" + toolName + "\")", "剩余工具：" + toolkit.getToolNames());
                return "已卸载工具 " + toolName + "，下一轮推理模型将看不到它。";
            }

            log.add("tool", "本轮可用工具", String.join(", ", toolkit.getToolNames()));
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            return Tracer.of(log).run(agent.streamEvents(List.of(new UserMessage(input)), ctx)).text();
        }

        private static String catalog(Toolkit toolkit) {
            return toolkit.getToolSchemas().stream()
                    .map((ToolSchema s) -> "- " + s.getName() + "：" + s.getDescription()
                            + "  参数 " + ((Map<?, ?>) s.getParameters().getOrDefault("properties", Map.of())).keySet())
                    .collect(Collectors.joining("\n"));
        }

        @Override
        public void reset() {
            this.agent = build();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
