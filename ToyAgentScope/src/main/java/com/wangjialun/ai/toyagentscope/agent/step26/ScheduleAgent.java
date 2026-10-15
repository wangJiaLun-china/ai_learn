package com.wangjialun.ai.toyagentscope.agent.step26;

import com.wangjialun.ai.toyagentscope.agent.Agent;
import com.wangjialun.ai.toyagentscope.agent.support.Json;
import com.wangjialun.ai.toyagentscope.agent.support.TraceLog;
import com.wangjialun.ai.toyagentscope.agent.support.Tracer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Step26 · 定时工具 —— 一次性 / 循环调度，触发结果追加到事件日志可回看。
 * <p>
 * 框架版：把调度能力做成 4 个 @Tool（schedule_once / schedule_repeat / list_jobs / cancel_job）+ 1 个查看工具，
 * 由模型理解「定时：5秒 提醒我喝水」「循环：每10秒 报一次时」这类自然语言并调用。
 * 任务触发时，再调用一个轻量 ReActAgent 把任务改写成提醒文案，结果用 Jackson 追加写入
 * data/events/step26-tasks.jsonl（顺手修掉手写版的两个 bug：JSON 未转义、触发计数不增长）。
 * <p>
 * 说明：AgentScope 有 agentscope-extensions-scheduler-quartz / xxl-job 扩展（按 Cron 调度整个智能体），
 * 2.0.4 只内置 DashScope 模型配置；教学里用 ScheduledExecutorService 演示同样的原理。
 * 为避免循环任务无限消耗 Token，循环任务最多触发 {@value #MAX_REPEAT} 次自动停止。
 */
public interface ScheduleAgent extends Agent {

    Path TASK_LOG = Path.of("data", "events", "step26-tasks.jsonl");
    int MAX_REPEAT = 5;
    int MIN_INTERVAL = 5;

    class Job {
        final String id;
        final String task;
        final String kind;
        final AtomicInteger fired = new AtomicInteger();
        ScheduledFuture<?> future;
        volatile String status = "等待中";

        Job(String id, String task, String kind) {
            this.id = id;
            this.task = task;
            this.kind = kind;
        }

        @Override
        public String toString() {
            return id + " [" + kind + "] " + task + " · 已触发 " + fired.get() + " 次 · " + status;
        }
    }

    class Impl implements ScheduleAgent, AutoCloseable {

        private final ReActAgent agent;
        private final ReActAgent reminder;
        private final TraceLog log = new TraceLog();
        private final Map<String, Job> jobs = new LinkedHashMap<>();
        private final AtomicInteger seq = new AtomicInteger();
        private final String owner = "s-" + UUID.randomUUID().toString().substring(0, 8);
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "step26-scheduler");
            t.setDaemon(true);
            return t;
        });

        public Impl(Model model) {
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new ScheduleTools());
            this.agent = ReActAgent.builder()
                    .name("scheduler")
                    .sysPrompt("你是定时任务助手。用户要求定时/循环/查看/取消任务时调用对应工具，再简短确认。循环间隔最少 "
                            + MIN_INTERVAL + " 秒。")
                    .model(model)
                    .toolkit(toolkit)
                    .maxIters(3)
                    .build();
            this.reminder = ReActAgent.builder()
                    .name("reminder")
                    .sysPrompt("你是提醒播报员，把任务改写成一句不超过 30 字的贴心提醒，可结合当前时间。")
                    .model(model)
                    .build();
        }

        public class ScheduleTools {

            @Tool(name = "schedule_once", description = "创建一次性定时任务：seconds 秒后执行 task")
            public String once(@ToolParam(name = "seconds", description = "延迟秒数") Integer seconds,
                               @ToolParam(name = "task", description = "任务内容，如 提醒我喝水") String task) {
                Job job = new Job("job-" + seq.incrementAndGet(), task, "一次性 " + seconds + "s");
                job.future = scheduler.schedule(() -> fire(job, true), Math.max(1, seconds), TimeUnit.SECONDS);
                jobs.put(job.id, job);
                return "已创建 " + job;
            }

            @Tool(name = "schedule_repeat", description = "创建循环任务：每 interval_seconds 秒执行一次 task（最多 " + MAX_REPEAT + " 次）")
            public String repeat(@ToolParam(name = "interval_seconds", description = "间隔秒数") Integer interval,
                                 @ToolParam(name = "task", description = "任务内容") String task) {
                int sec = Math.max(MIN_INTERVAL, interval == null ? MIN_INTERVAL : interval);
                Job job = new Job("job-" + seq.incrementAndGet(), task, "每 " + sec + "s");
                job.future = scheduler.scheduleAtFixedRate(() -> fire(job, false), sec, sec, TimeUnit.SECONDS);
                jobs.put(job.id, job);
                return "已创建 " + job;
            }

            @Tool(name = "list_jobs", description = "列出当前所有定时任务", readOnly = true)
            public String list() {
                return jobs.isEmpty() ? "（暂无任务）" : jobs.values().stream().map(Job::toString).collect(Collectors.joining("\n"));
            }

            @Tool(name = "cancel_job", description = "取消定时任务")
            public String cancel(@ToolParam(name = "job_id", description = "任务 ID，如 job-2") String jobId) {
                Job job = jobs.get(jobId);
                if (job == null) return "没有任务 " + jobId;
                job.future.cancel(false);
                job.status = "已取消";
                return "已取消 " + job;
            }

            @Tool(name = "fired_results", description = "查看已触发任务的执行结果（来自事件日志）", readOnly = true)
            public String fired() {
                try {
                    if (!Files.exists(TASK_LOG)) return "（还没有任务触发）";
                    List<String> mine = Files.readAllLines(TASK_LOG, StandardCharsets.UTF_8).stream()
                            .filter(l -> l.contains("\"owner\":\"" + owner + "\"")).toList();
                    return mine.isEmpty() ? "（还没有任务触发）" : String.join("\n", mine.subList(Math.max(0, mine.size() - 10), mine.size()));
                } catch (IOException e) {
                    return "读取失败: " + e.getMessage();
                }
            }
        }

        /** 任务触发：调用提醒智能体生成文案，追加写入事件日志。 */
        private void fire(Job job, boolean once) {
            int n = job.fired.incrementAndGet();
            String text;
            try {
                RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
                Msg m = reminder.call(List.of(new UserMessage("当前时间 "
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "，任务：" + job.task)), ctx).block();
                text = m == null ? job.task : m.getTextContent();
            } catch (Exception e) {
                text = "（生成提醒失败：" + e.getMessage() + "）" + job.task;
            }
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("ts", LocalDateTime.now().toString());
            ev.put("owner", owner);
            ev.put("job", job.id);
            ev.put("run", n);
            ev.put("task", job.task);
            ev.put("result", text);
            try {
                Files.createDirectories(TASK_LOG.getParent());
                Files.writeString(TASK_LOG, Json.write(ev) + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // 日志写失败不影响调度
            }
            if (once) {
                job.status = "已完成";
            } else if (n >= MAX_REPEAT) {
                job.future.cancel(false);
                job.status = "已达 " + MAX_REPEAT + " 次上限，自动停止";
            } else {
                job.status = "运行中";
            }
        }

        @Override
        public String name() {
            return "Step26 · 定时工具（一次性 / 循环调度）";
        }

        @Override
        public String chat(String input) {
            log.clear();
            RuntimeContext ctx = RuntimeContext.builder().userId("toy").sessionId(UUID.randomUUID().toString()).build();
            return Tracer.of(log).run(agent.streamEvents(List.of(new UserMessage(input)), ctx)).text();
        }

        @Override
        public void reset() {
            jobs.values().forEach(j -> j.future.cancel(false));
            jobs.clear();
        }

        @Override
        public List<Map<String, Object>> trace() {
            return log.snapshot();
        }

        @Override
        public void close() {
            scheduler.shutdownNow();
        }
    }
}
