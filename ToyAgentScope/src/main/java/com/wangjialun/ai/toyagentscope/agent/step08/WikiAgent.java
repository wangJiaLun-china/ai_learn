package com.wangjialun.ai.toyagentscope.agent.step08;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Texts;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Step08 · LLM-Wiki —— 知识「编译一次、持久维护、增量沉淀」。
 * <p>
 * 与 Step07 的区别：RAG 每次从原始文档临时检索；LLM-Wiki 把知识预先整理成条目（像项目里的
 * AGENTS.md / CLAUDE.md），对话中学到的新知识还能被「编译」回 Wiki，持久化到磁盘，下次直接可用。
 * <p>
 * 框架版：两个 @Tool —— wiki_search（查条目）与 wiki_compile（沉淀条目，写入 data/wiki/user-wiki.md），
 * 由模型判断「这是提问还是要我记住」。用户说「编译：主题：内容」或「记住…」时，模型会调用 wiki_compile。
 */
public interface WikiAgent extends Agent {

    /** 基础 Wiki：项目知识（生产中来自仓库里的 AGENTS.md）。 */
    Map<String, String> BASE_WIKI = Map.of(
            "架构约定", "ToyAgentScope 一个接口类就是一个智能体场景（step01-26），实现放在 Impl 内部类里；智能体内核统一使用 AgentScope Java 2.0 的 ReActAgent / HarnessAgent。",
            "代码规范", "Java 17 语法；类与核心方法必须有中文 Javadoc 说明教学意图；trace 事件用 type/label/detail 三段式；工具一律用 @Tool 注解声明。",
            "部署流程", "./gradlew run 启动，默认端口 8099，可用环境变量 TOY_AGENT_PORT 覆盖；打包用 ./gradlew shadowJar 生成 build/libs/ToyAgentScope.jar。",
            "业务规则", "模型配置优先取浏览器 localStorage 随请求携带的配置，服务端 config.properties 仅作默认兜底；本项目只支持真实模型。");

    Path USER_WIKI_FILE = Path.of("data", "wiki", "user-wiki.md");

    /** Wiki 存储：基础条目 + 用户增量条目（Markdown 文件持久化，进程内共享）。 */
    final class WikiStore {

        private WikiStore() {
        }

        static synchronized Map<String, String> all() {
            Map<String, String> m = new LinkedHashMap<>(BASE_WIKI);
            m.putAll(loadUser());
            return m;
        }

        static synchronized Map<String, String> loadUser() {
            Map<String, String> m = new LinkedHashMap<>();
            if (!Files.exists(USER_WIKI_FILE)) return m;
            try {
                String topic = null;
                StringBuilder body = new StringBuilder();
                for (String line : Files.readAllLines(USER_WIKI_FILE, StandardCharsets.UTF_8)) {
                    if (line.startsWith("## ")) {
                        if (topic != null) m.put(topic, body.toString().trim());
                        topic = line.substring(3).trim();
                        body.setLength(0);
                    } else if (topic != null) {
                        body.append(line).append('\n');
                    }
                }
                if (topic != null) m.put(topic, body.toString().trim());
            } catch (IOException e) {
                System.err.println("[WikiAgent] 读取 Wiki 失败: " + e.getMessage());
            }
            return m;
        }

        static synchronized void compile(String topic, String content) throws IOException {
            Map<String, String> user = loadUser();
            user.put(topic.trim(), content.trim());
            Files.createDirectories(USER_WIKI_FILE.getParent());
            String md = "# 用户沉淀的项目 Wiki\n\n" + user.entrySet().stream()
                    .map(e -> "## " + e.getKey() + "\n" + e.getValue() + "\n")
                    .collect(Collectors.joining("\n"));
            Files.writeString(USER_WIKI_FILE, md, StandardCharsets.UTF_8);
        }

        static synchronized void clearUser() throws IOException {
            Files.deleteIfExists(USER_WIKI_FILE);
        }
    }

    class WikiTools {

        @Tool(name = "wiki_search", description = "检索项目 Wiki 条目，返回最相关的 Top2 条目（含条目名）", readOnly = true)
        public String search(@ToolParam(name = "query", description = "要查询的问题或关键词") String query) {
            String hits = WikiStore.all().entrySet().stream()
                    .map(e -> Map.entry(e, Texts.bigramScore(query, e.getKey() + e.getValue())))
                    .filter(e -> e.getValue() > 0.05)
                    .sorted(Map.Entry.<Map.Entry<String, String>, Double>comparingByValue(Comparator.reverseOrder()))
                    .limit(2)
                    .map(e -> "【" + e.getKey().getKey() + "】" + e.getKey().getValue())
                    .collect(Collectors.joining("\n"));
            return hits.isEmpty() ? "项目 Wiki 中没有相关条目" : hits;
        }

        @Tool(name = "wiki_compile", description = "把一条新知识编译（沉淀）进项目 Wiki 并持久化；用户说「编译：主题：内容」或「记住…」时调用")
        public String compile(@ToolParam(name = "topic", description = "条目主题，如 发布流程") String topic,
                              @ToolParam(name = "content", description = "条目内容") String content) {
            try {
                WikiStore.compile(topic, content);
                return "已编译条目【" + topic + "】并写入 " + USER_WIKI_FILE;
            } catch (IOException e) {
                return "编译失败: " + e.getMessage();
            }
        }
    }

    class Impl implements WikiAgent {

        private final ReActAgent agent;
        private final TraceLog log = new TraceLog();

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new WikiTools());
            this.agent = ReActAgent.builder()
                    .name("wiki")
                    .sysPrompt("""
                            你是项目 Wiki 智能体。
                            1. 用户提问时，先调用 wiki_search，依据条目回答并标注来源条目名，如（来源：架构约定）；
                            2. 条目不足以回答时，如实说"项目 Wiki 中暂无相关条目"，并提示可以用「编译：主题：内容」沉淀；禁止编造；
                            3. 用户要求「编译：主题：内容」或「记住…」时，调用 wiki_compile 沉淀知识，然后简短确认。
                            """)
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(4)
                    .build();
        }

        @Override
        public String name() {
            return "Step08 · LLM-Wiki 知识编译（持久维护）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            log.add("load", "加载 Wiki", "基础条目 " + BASE_WIKI.size() + " 条 + 用户沉淀 "
                    + WikiStore.loadUser().size() + " 条（" + USER_WIKI_FILE + "）");
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            return Tracer.of(log)
                    .observationType(tool -> switch (tool) {
                        case "wiki_search" -> "retrieve";
                        case "wiki_compile" -> "compile";
                        default -> "observation";
                    })
                    .run(agent.streamEvents(List.of(new UserMessage(input)), ctx))
                    .text();
        }

        @Override
        public void reset() {
            try {
                WikiStore.clearUser();
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
