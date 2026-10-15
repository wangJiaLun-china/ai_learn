package com.wangjialun.ai.toyagentscope;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.Json;
import com.wangjialun.ai.toyagentscope.llm.Models;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * ToyAgentScope 服务入口（JDK 自带 HttpServer，与手写版 ToyAgent 的 HTTP 契约完全一致）。
 * <p>
 * 1. 托管 web/ 静态页面（直接复用手写版的测试页）；
 * 2. 暴露 /api/{stepId}/chat 与 /api/{stepId}/reset，把 26 个 AgentScope 场景接入页面；
 * 3. 模型配置可随请求携带（浏览器 localStorage），会话缓存键 = 会话 ID + 模型指纹，配置一变即重建。
 * <p>
 * 启动：./gradlew run（默认端口 8099，可用 TOY_AGENT_PORT 覆盖）
 */
public final class Application {

    /** 场景工厂：给定模型，造一个全新的场景实例。 */
    private static final Map<String, Function<Model, Agent>> FACTORIES = Scenarios.all();

    /** 会话级实例缓存：(scopeId|模型指纹) -> (stepId -> Agent)。 */
    private static final Map<String, Map<String, Agent>> SESSIONS = new ConcurrentHashMap<>();

    private static final int MAX_SESSIONS = 512;
    private static final String DEFAULT_SCOPE = "default";

    /** 服务端口（Step14 的 A2A 回环调用需要知道自己在哪个端口）。 */
    public static volatile int port = 8099;

    public static void main(String[] args) throws IOException {
        port = Integer.parseInt(System.getenv().getOrDefault("TOY_AGENT_PORT", "8099"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(32));
        Scenarios.mountExtraRoutes(server);
        server.createContext("/", Application::dispatch);
        server.start();

        Models.Config def = Models.serverDefault();
        System.out.println("==============================================");
        System.out.println("  ToyAgentScope v1.0 (AgentScope Java 2.0.4) 启动成功");
        System.out.println("  默认模型: " + (def.complete() ? def.model() + " @ " + def.baseUrl() : "未配置（请填写 config.properties）"));
        System.out.println("  测试页面: http://localhost:" + port);
        System.out.println("==============================================");
    }

    // ------------------------------------------------------------------ 路由

    private static void dispatch(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else if (path.startsWith("/source/")) {
                handleSource(exchange, path.substring("/source/".length()));
            } else {
                handleStatic(exchange, path);
            }
        } catch (Exception e) {
            try {
                respond(exchange, 500, errorJson("服务内部错误: " + e.getMessage()));
            } catch (IOException ignored) {
                // 响应已不可写，忽略
            }
        } finally {
            exchange.close();
        }
    }

    private static void handleApi(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();

        if (path.equals("/api/config") && "GET".equals(method)) {
            respond(exchange, 200, Json.write(Map.of("mode", "real", "model", Models.serverDefault().model())));
            return;
        }

        if (path.equals("/api/model") && "GET".equals(method)) {
            Models.Config def = Models.serverDefault();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mode", "real");
            m.put("baseUrl", def.baseUrl());
            m.put("apiKeyMasked", mask(def.apiKey()));
            m.put("model", def.model());
            respond(exchange, 200, Json.write(m));
            return;
        }

        if (path.equals("/api/model/test") && "POST".equals(method)) {
            try {
                Map<String, Object> req = Json.readMap(readBody(exchange));
                Models.Config cfg = Models.resolve(Json.str(req, "baseUrl"), Json.str(req, "apiKey"), Json.str(req, "model"));
                Model tester = Models.build(cfg);
                String reply = tester.stream(List.of(new UserMessage("回复：pong")), List.of(), GenerateOptions.builder().build())
                        .collectList().block().stream()
                        .map(r -> r.getContent() == null ? "" : r.getContent().stream()
                                .filter(b -> b instanceof io.agentscope.core.message.TextBlock)
                                .map(b -> ((io.agentscope.core.message.TextBlock) b).getText())
                                .reduce("", String::concat))
                        .reduce("", String::concat);
                respond(exchange, 200, Json.write(Map.of("ok", true, "reply", reply)));
            } catch (Exception e) {
                respond(exchange, 200, Json.write(Map.of("ok", false, "error", String.valueOf(e.getMessage()))));
            }
            return;
        }

        // ---------------- 场景交互 ----------------
        String[] parts = path.split("/");
        if (parts.length != 4) {
            respond(exchange, 404, errorJson("路径不存在: " + path));
            return;
        }
        String stepId = parts[2];
        String action = parts[3];
        Function<Model, Agent> factory = FACTORIES.get(stepId);
        if (factory == null) {
            respond(exchange, 404, errorJson("场景不存在: " + stepId));
            return;
        }

        Map<String, Object> body = null;
        if ("POST".equals(method)) {
            String raw = readBody(exchange);
            try {
                body = raw.isBlank() ? Map.of() : Json.readMap(raw);
            } catch (Exception e) {
                body = Map.of();
            }
        }

        Models.Config cfg;
        try {
            cfg = browserModel(body);
        } catch (IllegalArgumentException e) {
            respond(exchange, 200, errorJson(e.getMessage()));
            return;
        }
        if (!cfg.complete()) {
            respond(exchange, 200, errorJson("未配置模型：请在 config.properties 填写 base-url / api-key / model，或点击页面右上角配置模型"));
            return;
        }

        String sessionKey = scopeOf(exchange) + "|" + cfg.fingerprint();
        if ("reset".equals(action)) {
            Agent existing = SESSIONS.getOrDefault(sessionKey, Map.of()).get(stepId);
            if (existing != null) {
                synchronized (existing) {
                    existing.reset();
                }
            }
            respond(exchange, 200, Json.write(Map.of("ok", true)));
            return;
        }

        if (!"chat".equals(action) || !"POST".equals(method)) {
            respond(exchange, 405, errorJson("不支持的请求: " + method + " " + path));
            return;
        }

        String message = Json.str(body, "message");
        if (message == null || message.isBlank()) {
            respond(exchange, 400, errorJson("message 不能为空"));
            return;
        }

        long start = System.currentTimeMillis();
        try {
            Agent agent = sessionAgent(sessionKey, stepId, () -> factory.apply(Models.model(cfg)));
            Map<String, Object> resp = new LinkedHashMap<>();
            // 同一会话的同一场景串行执行（智能体实例有状态）
            synchronized (agent) {
                String answer = agent.chat(message);
                resp.put("id", stepId);
                resp.put("name", agent.name());
                resp.put("answer", answer);
                resp.put("trace", agent.trace());
            }
            resp.put("ms", System.currentTimeMillis() - start);
            respond(exchange, 200, Json.write(resp));
        } catch (Exception e) {
            e.printStackTrace();
            respond(exchange, 500, errorJson("智能体执行失败: " + rootMessage(e)));
        }
    }

    /** 请求体里的浏览器级模型配置；本项目只支持真实模型。 */
    private static Models.Config browserModel(Map<String, Object> body) {
        if (body == null || !(body.get("model") instanceof Map<?, ?> m)) return Models.serverDefault();
        if (Boolean.TRUE.equals(m.get("mock"))) {
            throw new IllegalArgumentException("本项目仅支持真实模型（AgentScope 版没有 Mock），请在右上角配置 OpenAI 兼容模型或清除浏览器里的 Mock 设置");
        }
        return Models.resolve(Json.str(m, "baseUrl"), Json.str(m, "apiKey"), Json.str(m, "model"));
    }

    // ------------------------------------------------------------------ 会话隔离

    private static String scopeOf(HttpExchange exchange) {
        String raw = exchange.getRequestHeaders().getFirst("X-Scope-Id");
        if (raw == null || raw.isBlank()) return DEFAULT_SCOPE;
        String cleaned = raw.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase();
        if (cleaned.isEmpty()) return DEFAULT_SCOPE;
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    private static Agent sessionAgent(String key, String stepId, java.util.function.Supplier<Agent> factory) {
        if (SESSIONS.size() > MAX_SESSIONS) {
            SESSIONS.values().forEach(m -> m.values().forEach(Application::closeQuietly));
            SESSIONS.clear();
        }
        return SESSIONS.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(stepId, id -> factory.get());
    }

    private static void closeQuietly(Agent a) {
        if (a instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // 尽力而为
            }
        }
    }

    // ------------------------------------------------------------------ 源码直达

    /** GET /source/stepXX：输出该场景包下的全部 Java 源码（页面「源码」按钮使用）。 */
    private static void handleSource(HttpExchange exchange, String id) throws IOException {
        Path root = locateProjectRoot().resolve(Path.of("src", "main", "java", "com", "wangjialun", "ai", "toyagentscope"));
        List<Path> files;
        if (id.matches("step\\d{2}")) {
            Path dir = root.resolve("agent").resolve(id);
            if (!Files.isDirectory(dir)) {
                respond(exchange, 404, "找不到源码目录 " + dir + "（请在项目根目录启动服务）", "text/plain; charset=utf-8");
                return;
            }
            try (var walk = Files.walk(dir)) {
                files = walk.filter(f -> f.toString().endsWith(".java")).sorted().toList();
            }
        } else {
            files = List.of(root.resolve("Application.java"), root.resolve("Scenarios.java"));
        }
        StringBuilder sb = new StringBuilder();
        for (Path f : files) {
            if (!Files.exists(f)) continue;
            sb.append("// ==================== ").append(f).append(" ====================\n\n")
                    .append(Files.readString(f, StandardCharsets.UTF_8)).append("\n\n");
        }
        respond(exchange, 200, sb.length() == 0 ? "源码不可用" : sb.toString(), "text/plain; charset=utf-8");
    }

    // ------------------------------------------------------------------ 静态资源

    /** IDEA 从仓库根启动时，模块文件在 ToyAgentScope/ 下。 */
    private static Path locateProjectRoot() {
        Path direct = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(direct.resolve("web"))) return direct;
        Path nested = direct.resolve("ToyAgentScope");
        if (Files.isDirectory(nested.resolve("web"))) return nested;
        return direct;
    }

    private static Path locateWebRoot() {
        return locateProjectRoot().resolve("web");
    }

    private static void handleStatic(HttpExchange exchange, String path) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, errorJson("仅支持 GET"));
            return;
        }
        String rel = path.equals("/") ? "index.html" : path.substring(1);
        Path webRoot = locateWebRoot();
        Path target = webRoot.resolve(rel).normalize();

        if (!target.startsWith(webRoot) || !Files.exists(target) || Files.isDirectory(target)) {
            if (!rel.contains(".")) {
                target = webRoot.resolve("index.html");
            } else {
                respond(exchange, 404, "404 Not Found: " + rel);
                return;
            }
        }

        String contentType = switch (rel.substring(rel.lastIndexOf('.') + 1)) {
            case "html" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js" -> "application/javascript; charset=utf-8";
            case "json" -> "application/json; charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "ico" -> "image/x-icon";
            default -> "application/octet-stream";
        };
        byte[] bytes = Files.readAllBytes(target);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ------------------------------------------------------------------ 响应工具

    public static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    public static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, body, "application/json; charset=utf-8");
    }

    public static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String errorJson(String msg) {
        return Json.write(Map.of("error", msg));
    }

    private static String mask(String key) {
        if (key == null || key.length() < 8) return key == null || key.isEmpty() ? "" : "****";
        return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
