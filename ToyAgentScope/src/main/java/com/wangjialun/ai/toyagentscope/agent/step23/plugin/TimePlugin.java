package com.wangjialun.ai.toyagentscope.agent.step23.plugin;

import com.wangjialun.ai.toyagentscope.agent.step23.PluginAgent;
import io.agentscope.core.tool.Tool;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一个示例插件：贡献 uptime / coin_flip 两个 @Tool。
 * <p>
 * 安装时它的 .class 文件会被复制到 data/plugins/step23/，再由隔离的 ClassLoader 从那里加载 ——
 * 宿主代码里没有任何地方 new 它，只认 {@link PluginAgent.AgentPlugin} 契约。
 */
public class TimePlugin implements PluginAgent.AgentPlugin {

    private long startedAt;

    @Override
    public String id() {
        return "time-plugin";
    }

    @Override
    public void onStart() {
        startedAt = System.currentTimeMillis();
    }

    @Override
    public List<Object> tools() {
        return List.of(new TimeTools());
    }

    @Override
    public void onStop() {
        startedAt = 0;
    }

    /** 插件贡献的工具。 */
    public static class TimeTools {

        @Tool(name = "uptime", description = "查询宿主 JVM 已运行多久", readOnly = true)
        public String uptime() {
            Duration d = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
            return "宿主已运行 " + d.toHours() + " 小时 " + d.toMinutesPart() + " 分 " + d.toSecondsPart() + " 秒";
        }

        @Tool(name = "coin_flip", description = "掷一枚硬币，返回正面或反面", readOnly = true)
        public String coinFlip() {
            return ThreadLocalRandom.current().nextBoolean() ? "正面" : "反面";
        }
    }
}
