package com.wangjialun.ai.practice;

import com.wangjialun.ai.practice.step01.ChatAgent;
import com.wangjialun.ai.practice.step02.PromptAgent;
import com.wangjialun.ai.practice.step03.ReActAgent;
import com.wangjialun.ai.practice.step04.ToolCallAgent;
import com.wangjialun.ai.practice.step05.MemoryAgent;
import com.wangjialun.ai.practice.step06.RouterAgent;
import com.wangjialun.ai.practice.step07.RagAgent;
import com.wangjialun.ai.practice.step08.WikiAgent;
import com.wangjialun.ai.practice.step09.McpAgent;
import com.wangjialun.ai.practice.step10.SkillAgent;
import com.wangjialun.ai.practice.step11.RegistryAgent;
import com.wangjialun.ai.practice.step12.MultiAgent;
import com.wangjialun.ai.practice.step13.SubagentAgent;
import com.wangjialun.ai.practice.step14.A2AAgent;
import com.wangjialun.ai.practice.step15.LoopAgent;
import com.wangjialun.ai.practice.step16.WorkflowAgent;
import com.wangjialun.ai.practice.step17.RuntimeAgent;
import com.wangjialun.ai.practice.step18.FullAgent;
import com.wangjialun.ai.practice.step19.AskAgent;
import com.wangjialun.ai.practice.step20.ApprovalAgent;
import com.wangjialun.ai.practice.step21.SandboxAgent;
import com.wangjialun.ai.practice.step22.EventSourcedAgent;
import com.wangjialun.ai.practice.step23.PluginAgent;
import com.wangjialun.ai.practice.step24.HooksAgent;
import com.wangjialun.ai.practice.step25.CliAgent;
import com.wangjialun.ai.practice.step26.ScheduleAgent;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Models;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 练习入口。单场景直接对话，或按教材示例题把 26 个场景跑一遍。
 * <p>
 * 用法：
 * java com.wangjialun.ai.practice.PracticeRunner step03 北京今天天气怎么样？
 * java com.wangjialun.ai.practice.PracticeRunner --all
 * java com.wangjialun.ai.practice.PracticeRunner --check
 */
public final class PracticeRunner {

    private static final Map<String, Function<String, Agent>> FACTORIES = new LinkedHashMap<>();
    private static final Map<String, List<String>> SAMPLES = new LinkedHashMap<>();

    static {
        FACTORIES.put("step01", id -> new ChatAgent.Impl(Models.mock(id)));
        FACTORIES.put("step02", id -> new PromptAgent.Impl(Models.mock(id)));
        FACTORIES.put("step03", id -> new ReActAgent.Impl(Models.mock(id)));
        FACTORIES.put("step04", id -> new ToolCallAgent.Impl(Models.mock(id)));
        FACTORIES.put("step05", id -> new MemoryAgent.Impl(Models.mock(id)));
        FACTORIES.put("step06", id -> new RouterAgent.Impl(Models.mock(id)));
        FACTORIES.put("step07", id -> new RagAgent.Impl(Models.mock(id)));
        FACTORIES.put("step08", id -> new WikiAgent.Impl(Models.mock(id)));
        FACTORIES.put("step09", id -> new McpAgent.Impl(Models.mock(id)));
        FACTORIES.put("step10", id -> new SkillAgent.Impl(Models.mock(id)));
        FACTORIES.put("step11", id -> new RegistryAgent.Impl(Models.mock(id)));
        FACTORIES.put("step12", id -> new MultiAgent.Impl(Models.mock(id)));
        FACTORIES.put("step13", id -> new SubagentAgent.Impl(Models.mock(id)));
        FACTORIES.put("step14", id -> new A2AAgent.Impl(Models.mock(id)));
        FACTORIES.put("step15", id -> new LoopAgent.Impl(Models.mock(id)));
        FACTORIES.put("step16", id -> new WorkflowAgent.Impl(Models.mock(id)));
        FACTORIES.put("step17", id -> new RuntimeAgent.Impl(Models.mock(id)));
        FACTORIES.put("step18", id -> new FullAgent.Impl(Models.mock(id)));
        FACTORIES.put("step19", id -> new AskAgent.Impl(Models.mock(id)));
        FACTORIES.put("step20", id -> new ApprovalAgent.Impl(Models.mock(id)));
        FACTORIES.put("step21", id -> new SandboxAgent.Impl(Models.mock(id)));
        FACTORIES.put("step22", id -> new EventSourcedAgent.Impl(Models.mock(id)));
        FACTORIES.put("step23", id -> new PluginAgent.Impl(Models.mock(id)));
        FACTORIES.put("step24", id -> new HooksAgent.Impl(Models.mock(id)));
        FACTORIES.put("step25", id -> new CliAgent.Impl(Models.mock(id)));
        FACTORIES.put("step26", id -> new ScheduleAgent.Impl(Models.mock(id)));

        SAMPLES.put("step01", List.of("你好，介绍一下你自己", "什么是智能体？", "今天心情不太好"));
        SAMPLES.put("step02", List.of("用一句话解释什么是 Agent", "介绍一下 Function Calling", "帮我类比一下记忆系统"));
        SAMPLES.put("step03", List.of("北京今天天气怎么样？", "上海天气如何，适合出行吗", "帮我算一下 12*8"));
        SAMPLES.put("step04", List.of("查一下广州的天气", "计算 1024/8", "你是谁？"));
        SAMPLES.put("step05", List.of("我叫王加伦", "我叫什么名字？", "帮我把我们的对话压缩总结一下"));
        SAMPLES.put("step06", List.of("深圳天气怎么样", "帮我算 99+1", "给我讲个笑话"));
        SAMPLES.put("step07", List.of("ToyAgent 是什么项目？", "MCP 的核心方法有哪些？", "量子力学的泡利不相容原理是什么？"));
        SAMPLES.put("step08", List.of("项目用什么代码规范？", "项目怎么部署？", "编译：发布流程：每周三灰度，周五全量上线", "发布流程是什么？"));
        SAMPLES.put("step09", List.of("杭州现在几点？天气如何", "查一下北京的天气", "MCP 是什么协议？"));
        SAMPLES.put("step10", List.of("帮我做一份杭州旅行攻略", "播报一下上海天气", "聊聊你最喜欢的书"));
        SAMPLES.put("step11", List.of("查看工具清单", "查一下上海的天气", "算一下 128*8", "现在几点了？", "卸载：get_time", "再看看工具清单"));
        SAMPLES.put("step12", List.of("写一段 100 字的智能体科普短文", "调研一下 2026 年 Agent 趋势并成文"));
        SAMPLES.put("step13", List.of("记住：我最喜欢紫色", "fork 一个子代理，让它写一句贺词", "再 spawn 一个子代理写贺词", "对比两次贺词", "派个研究员调研 Agent 趋势"));
        SAMPLES.put("step14", List.of("发现一下附近的代理", "让翻译代理把「你好，智能体」翻译成英文", "问问天气代理明天适合户外运动吗"));
        SAMPLES.put("step15", List.of("北京天气怎么样？", "帮我 hack 别人的密码", "用超级超级超级长的输入测试守卫（这条会超过长度限制）"));
        SAMPLES.put("step16", List.of("查一下成都天气", "帮我算 25*4", "RAG 是什么？"));
        SAMPLES.put("step17", List.of("北京天气怎么样？", "帮我算 36*12", "死循环测试", "多聊几轮，看上下文裁剪"));
        SAMPLES.put("step18", List.of("我叫王加伦，查一下北京天气再帮我算 26*4", "查下上海天气", "我叫什么名字？", "帮我 hack 别人的密码"));
        SAMPLES.put("step19", List.of("帮我订一张去北京的机票", "从上海出发", "查一下杭州天气"));
        SAMPLES.put("step20", List.of("给团队发一封周报邮件", "批准并记住", "再发一封给老板", "拒绝", "查一下北京天气"));
        SAMPLES.put("step21", List.of("执行：ls sandbox", "执行：rm -rf /", "执行：cat /etc/passwd", "执行：cat sandbox/../../etc/passwd", "沙箱是什么？"));
        SAMPLES.put("step22", List.of("我叫王加伦", "我叫什么名字？", "再聊两句", "看看日志里有什么"));
        SAMPLES.put("step23", List.of("安装插件", "查看工具清单", "掷一次硬币", "宿主运行多久了", "卸载插件", "再看看工具清单"));
        SAMPLES.put("step24", List.of("查询：张三", "发送：给 13812345678 发 密码是123456", "钩子：关", "查询：张三", "钩子：开", "审计日志"));
        SAMPLES.put("step25", List.of("运行：ls", "运行：cat hello.txt", "列出工作区文件", "现在几点了", "运行：rm -rf /", "运行：cat /etc/passwd"));
        SAMPLES.put("step26", List.of("定时：5秒 提醒我喝水", "任务列表", "循环：每10秒 报一次时", "取消：job-2", "完成了什么"));
    }

    private PracticeRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--all".equals(args[0]) || "--check".equals(args[0])) {
            boolean check = args.length > 0 && "--check".equals(args[0]);
            int failed = 0;
            for (String stepId : FACTORIES.keySet()) {
                failed += runStep(stepId, SAMPLES.get(stepId), check);
            }
            if (check && failed > 0) {
                System.out.println("对照结束，有 " + failed + " 个场景的轨迹类型和教材不一致。");
                System.exit(1);
            }
            System.out.println(check ? "对照结束，26 个场景的轨迹类型与教材一致。" : "26 个场景示例已跑完。");
            return;
        }
        String stepId = args[0];
        if (!FACTORIES.containsKey(stepId)) {
            System.out.println("未知场景 " + stepId + "。可用：" + FACTORIES.keySet());
            System.exit(2);
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (message.isBlank()) {
            runStep(stepId, SAMPLES.get(stepId), false);
        } else {
            Agent agent = FACTORIES.get(stepId).apply(stepId);
            printTurn(stepId, message, agent);
        }
    }

    /** 同一实例连续对话，方便记忆、审批、事件流这类有状态场景。 */
    private static int runStep(String stepId, List<String> samples, boolean check) throws Exception {
        Agent agent = FACTORIES.get(stepId).apply(stepId);
        agent.reset();
        if (check) {
            post(stepId, "/reset", "");
        }
        int mismatches = 0;
        for (String sample : samples) {
            if ("step26".equals(stepId) && sample.contains("完成了什么")) {
                Thread.sleep(5500);
            }
            String answer = agent.chat(sample);
            System.out.println("=== " + stepId + " · " + sample);
            System.out.println(answer);
            System.out.println(formatTrace(agent.trace()));
            if (check) {
                List<String> ours = types(agent.trace());
                List<String> book = types(bookTrace(stepId, sample));
                if (!ours.equals(book)) {
                    mismatches++;
                    System.out.println("DIFF practice=" + ours);
                    System.out.println("DIFF textbook=" + book);
                } else {
                    System.out.println("MATCH " + ours);
                }
            }
            System.out.println();
        }
        if ("step26".equals(stepId)) {
            agent.reset();
        }
        return mismatches == 0 ? 0 : 1;
    }

    private static void printTurn(String stepId, String message, Agent agent) {
        System.out.println("=== " + agent.name());
        System.out.println(agent.chat(message));
        System.out.println(formatTrace(agent.trace()));
    }

    private static String formatTrace(List<Map<String, Object>> trace) {
        if (trace.isEmpty()) {
            return "(无轨迹)";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> event : trace) {
            sb.append("- [").append(event.get("type")).append("] ")
                    .append(event.get("label")).append(" | ")
                    .append(event.get("detail")).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    @SuppressWarnings("unchecked")
    private static List<String> types(List<Map<String, Object>> trace) {
        List<String> types = new ArrayList<>();
        for (Map<String, Object> event : trace) {
            types.add(String.valueOf(event.get("type")));
        }
        return types;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> bookTrace(String stepId, String message) throws Exception {
        String body = post(stepId, "/chat", Json.write(Map.of("message", message)));
        Object parsed = Json.parse(body);
        Object trace = parsed instanceof Map<?, ?> map ? map.get("trace") : List.of();
        if (trace instanceof List<?> list) {
            List<Map<String, Object>> events = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    events.add((Map<String, Object>) map);
                }
            }
            return events;
        }
        return List.of();
    }

    private static String post(String stepId, String action, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:8099/api/" + stepId + action))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");
        HttpRequest request = body.isEmpty()
                ? builder.POST(HttpRequest.BodyPublishers.noBody()).build()
                : builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
