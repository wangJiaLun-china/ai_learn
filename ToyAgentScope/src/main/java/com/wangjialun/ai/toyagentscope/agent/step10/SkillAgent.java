package com.wangjialun.ai.toyagentscope.agent.step10;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.CommonTools;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Step10 · 技能编排 —— Skills 的 L0 / L1 / L2 渐进式披露。
 * <p>
 * 手写版：先让模型选技能（JSON），再在 Java 里按技能名 switch 执行固定流程。
 * <p>
 * 框架版：技能是一个目录 + SKILL.md（resources/skills/*），由 {@link ClasspathSkillRepository} 加载：
 * <ul>
 *   <li><b>L0 元数据</b>：所有技能的 name + description 常驻系统提示词，成本极低；</li>
 *   <li><b>L1 说明书</b>：模型判断要用某技能时，调用内置工具 load_skill_through_path 读取 SKILL.md；</li>
 *   <li><b>L2 资源</b>：SKILL.md 里引用的 references/*.md 等，执行到那一步才按需加载。</li>
 * </ul>
 * 「选哪个技能、按什么步骤做」全部由模型读说明书决定，Java 代码里没有任何 if/switch。
 */
public interface SkillAgent extends Agent {

    class Impl implements SkillAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new CommonTools());
            try {
                this.agent = ReActAgent.builder()
                        .name("skill")
                        .sysPrompt("你是一个会使用技能的助手。如果某个技能与用户请求匹配，先加载该技能的 SKILL.md 并严格按其步骤执行；都不匹配时直接自由回答。")
                        .model(model)
                        .toolkit(toolkit)
                        .skillRepository(new ClasspathSkillRepository("skills"))
                        .maxIters(8)
                        .build();
            } catch (IOException e) {
                throw new UncheckedIOException("加载技能失败", e);
            }
        }

        @Override
        public String name() {
            return "Step10 · 技能编排智能体（Skills L0/L1/L2）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            log.add("skill", "L0 技能目录", "travel_plan：旅行攻略；weather_report：天气播报（name + description 常驻系统提示词）");
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            return Tracer.of(log)
                    .actionType(tool -> tool.startsWith("load_skill") ? "skill" : "action")
                    .observationType(tool -> tool.startsWith("load_skill") ? "skill" : "observation")
                    .run(agent.streamEvents(List.of(new UserMessage(input)), ctx))
                    .text();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }
    }
}
