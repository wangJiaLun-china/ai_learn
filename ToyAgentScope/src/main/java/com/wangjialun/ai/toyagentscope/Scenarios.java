package com.wangjialun.ai.toyagentscope;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.step01.ChatAgent;
import com.wangjialun.ai.toyagentscope.agent.step02.PromptAgent;
import com.wangjialun.ai.toyagentscope.agent.step03.ReActAgent;
import com.wangjialun.ai.toyagentscope.agent.step04.ToolCallAgent;
import com.wangjialun.ai.toyagentscope.agent.step05.MemoryAgent;
import com.wangjialun.ai.toyagentscope.agent.step06.RouterAgent;
import com.wangjialun.ai.toyagentscope.agent.step07.RagAgent;
import com.wangjialun.ai.toyagentscope.agent.step08.WikiAgent;
import com.wangjialun.ai.toyagentscope.agent.step09.McpAgent;
import com.wangjialun.ai.toyagentscope.agent.step10.SkillAgent;
import com.wangjialun.ai.toyagentscope.agent.step11.RegistryAgent;
import com.wangjialun.ai.toyagentscope.agent.step12.MultiAgent;
import com.wangjialun.ai.toyagentscope.agent.step13.SubagentAgent;
import com.wangjialun.ai.toyagentscope.agent.step14.A2AAgent;
import com.wangjialun.ai.toyagentscope.agent.step14.A2AServerHost;
import com.wangjialun.ai.toyagentscope.agent.step15.LoopAgent;
import com.wangjialun.ai.toyagentscope.agent.step16.WorkflowAgent;
import com.wangjialun.ai.toyagentscope.agent.step17.RuntimeAgent;
import com.wangjialun.ai.toyagentscope.agent.step18.FullAgent;
import com.wangjialun.ai.toyagentscope.agent.step19.AskAgent;
import com.wangjialun.ai.toyagentscope.agent.step20.ApprovalAgent;
import com.wangjialun.ai.toyagentscope.agent.step21.SandboxAgent;
import com.wangjialun.ai.toyagentscope.agent.step22.EventSourcedAgent;
import com.wangjialun.ai.toyagentscope.agent.step23.PluginAgent;
import com.wangjialun.ai.toyagentscope.agent.step24.HooksAgent;
import com.wangjialun.ai.toyagentscope.agent.step25.CliAgent;
import com.wangjialun.ai.toyagentscope.agent.step26.ScheduleAgent;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.model.Model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 26 个场景的注册表：stepId → 「给定模型造场景实例」的工厂。
 */
final class Scenarios {

    private Scenarios() {
    }

    static Map<String, Function<Model, Agent>> all() {
        Map<String, Function<Model, Agent>> m = new LinkedHashMap<>();
        m.put("step01", ChatAgent.Impl::new);
        m.put("step02", PromptAgent.Impl::new);
        m.put("step03", ReActAgent.Impl::new);
        m.put("step04", ToolCallAgent.Impl::new);
        m.put("step05", MemoryAgent.Impl::new);
        m.put("step06", RouterAgent.Impl::new);
        m.put("step07", RagAgent.Impl::new);
        m.put("step08", WikiAgent.Impl::new);
        m.put("step09", McpAgent.Impl::new);
        m.put("step10", SkillAgent.Impl::new);
        m.put("step11", RegistryAgent.Impl::new);
        m.put("step12", MultiAgent.Impl::new);
        m.put("step13", SubagentAgent.Impl::new);
        m.put("step14", A2AAgent.Impl::new);
        m.put("step15", LoopAgent.Impl::new);
        m.put("step16", WorkflowAgent.Impl::new);
        m.put("step17", RuntimeAgent.Impl::new);
        m.put("step18", FullAgent.Impl::new);
        m.put("step19", AskAgent.Impl::new);
        m.put("step20", ApprovalAgent.Impl::new);
        m.put("step21", SandboxAgent.Impl::new);
        m.put("step22", EventSourcedAgent.Impl::new);
        m.put("step23", PluginAgent.Impl::new);
        m.put("step24", HooksAgent.Impl::new);
        m.put("step25", CliAgent.Impl::new);
        m.put("step26", ScheduleAgent.Impl::new);
        return m;
    }

    /** 场景需要的额外 HTTP 路由（如 Step14 的 A2A 服务端）。 */
    static void mountExtraRoutes(HttpServer server) {
        // Step14：把两个「远端」A2A 代理发布在 /a2a/{name}
        server.createContext("/a2a/", A2AServerHost::handle);
    }
}
