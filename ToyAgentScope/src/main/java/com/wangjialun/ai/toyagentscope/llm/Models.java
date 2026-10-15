package com.wangjialun.ai.toyagentscope.llm;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.compat.deepseek.DeepSeekFormatter;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型工厂：把「OpenAI 兼容配置」装配成 AgentScope 的 {@link Model}。
 * <p>
 * 手写版 ToyAgent 用 java.net.http 自己拼 /chat/completions 请求；
 * 这里换成 AgentScope 的 {@link OpenAIChatModel}：流式、Function Calling、结构化输出、
 * 思考内容（reasoning）解析全部由框架负责。本项目只用真实模型，没有 Mock。
 * <p>
 * 服务端默认配置优先级：config.properties 非空值 &gt; LLM_* 环境变量 &gt; OPENAI_* 环境变量。
 */
public final class Models {

    /** 一份模型配置（apiKey 不参与 toString，避免日志泄露）。 */
    public record Config(String baseUrl, String apiKey, String model) {

        public boolean complete() {
            return notBlank(baseUrl) && notBlank(apiKey) && notBlank(model);
        }

        /** 配置指纹：用于会话缓存键，配置变化即重建该会话的智能体。 */
        public String fingerprint() {
            return baseUrl + "|" + model + "|" + Integer.toHexString(String.valueOf(apiKey).hashCode());
        }

        @Override
        public String toString() {
            return "Config[" + baseUrl + ", " + model + "]";
        }
    }

    private static final Map<String, Model> CACHE = new ConcurrentHashMap<>();
    private static volatile Config serverDefault = loadServerDefault();

    private Models() {
    }

    /** 服务端默认配置。 */
    public static Config serverDefault() {
        return serverDefault;
    }

    /**
     * 解析浏览器携带的模型配置：缺 baseUrl/model 时用服务端默认，缺 apiKey 时回退服务端 Key（Key 不出服务端）。
     */
    public static Config resolve(String baseUrl, String apiKey, String model) {
        Config def = serverDefault;
        if (!notBlank(baseUrl) || !notBlank(model)) return def;
        return new Config(baseUrl.trim(), notBlank(apiKey) ? apiKey.trim() : def.apiKey(), model.trim());
    }

    /** 按配置取（必要时创建）模型实例，同配置复用。 */
    public static Model model(Config cfg) {
        if (!cfg.complete()) {
            throw new IllegalStateException("未配置模型：请在 config.properties 填写 base-url / api-key / model，或在页面右上角配置");
        }
        return CACHE.computeIfAbsent(cfg.fingerprint(), k -> build(cfg));
    }

    /** 构建 OpenAI 兼容模型；DeepSeek 系列换用 DeepSeekFormatter（正确回传 reasoning_content）。 */
    public static Model build(Config cfg) {
        OpenAIChatModel.Builder b = OpenAIChatModel.builder()
                .baseUrl(cfg.baseUrl())
                .apiKey(cfg.apiKey())
                .modelName(cfg.model())
                .stream(true)
                // 结构化输出走「generate_response 工具调用」而不是 response_format：
                // 很多 OpenAI 兼容服务（如火山方舟）对 json_schema 支持不完整，工具调用路径更稳定
                .nativeStructuredOutput(false)
                .nativeStructuredOutputWithTools(false);
        if (cfg.model().toLowerCase().contains("deepseek")) {
            b.formatter(new DeepSeekFormatter());
        }
        return b.build();
    }

    /**
     * 先看启动目录，再看 ToyAgentScope 子目录。
     * IDEA 未改工作目录时 user.dir 是仓库根，配置实际在 ToyAgentScope/config.properties。
     */
    private static Path locateConfig() {
        Path direct = Path.of("config.properties").toAbsolutePath().normalize();
        if (Files.isRegularFile(direct)) return direct;
        Path nested = Path.of("ToyAgentScope", "config.properties").toAbsolutePath().normalize();
        if (Files.isRegularFile(nested)) return nested;
        return direct;
    }

    private static Config loadServerDefault() {
        Properties p = new Properties();
        Path configFile = locateConfig();
        if (Files.isRegularFile(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.err.println("[Models] 读取 config.properties 失败: " + e.getMessage());
            }
        }
        String baseUrl = pick(p.getProperty("base-url"), System.getenv("LLM_BASE_URL"), System.getenv("OPENAI_BASE_URL"), "https://api.deepseek.com/v1");
        String apiKey = pick(p.getProperty("api-key"), System.getenv("LLM_API_KEY"), System.getenv("OPENAI_API_KEY"), "");
        String model = pick(p.getProperty("model"), System.getenv("LLM_MODEL"), System.getenv("OPENAI_MODEL"), "deepseek-chat");
        return new Config(baseUrl, apiKey, model);
    }

    private static String pick(String... values) {
        for (String v : values) {
            if (notBlank(v)) return v.trim();
        }
        return "";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
