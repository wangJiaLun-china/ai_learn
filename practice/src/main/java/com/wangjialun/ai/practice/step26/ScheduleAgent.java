package com.wangjialun.ai.practice.step26;

import com.wangjialun.ai.practice.kit.Texts;
import com.wangjialun.ai.practice.kit.TraceLog;
import com.wangjialun.ai.practice.kit.WorkFiles;
import com.wangjialun.ai.practice.Agent;
import com.wangjialun.ai.practice.llm.ChatModel;
import com.wangjialun.ai.practice.llm.Json;
import com.wangjialun.ai.practice.llm.Message;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step26 · 定时工具。请求先返回，任务到点后在后台执行，结果追加到事件日志。
 */
public interface ScheduleAgent extends Agent {

    class Impl implements ScheduleAgent {

        private static final String SYSTEM = """
                你讲解定时任务。只输出 {"final":"回答"}。
                """;

        /**
         * 一个已注册任务。
         *
         * @param id 任务编号
         * @param kind 一次性或循环
         * @param content 到点要做的事
         * @param periodSeconds 延迟或周期秒数
         * @param future 调度句柄，取消时用
         * @param fired 已触发次数
         */
        private record Job(String id, String kind, String content, long periodSeconds,
                           ScheduledFuture<?> future, AtomicInteger fired) {
        }

        private final ChatModel model;
        private final ScheduledExecutorService pool = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "practice-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<String, Job> jobs = new LinkedHashMap<>();
        /** 已触发记录，调度线程和对话线程都会写。 */
        private final List<String> firedLog = new ArrayList<>();
        private final Path eventFile = WorkFiles.resolve("events", "step26-tasks.jsonl");
        private final AtomicInteger sequence = new AtomicInteger();
        private final TraceLog trace = new TraceLog();

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step26 · 定时工具（一次性/循环调度 · 触发结果事件落盘）";
        }

        @Override
        public String chat(String input) {
            trace.clear();
            Matcher once = Pattern.compile("定时：(\\d+)\\s*秒\\s*(.*)").matcher(input);
            if (once.matches()) {
                return once(Long.parseLong(once.group(1)), once.group(2).trim());
            }
            Matcher loop = Pattern.compile("循环：每(\\d+)\\s*秒\\s*(.*)").matcher(input);
            if (loop.matches()) {
                return recurring(Long.parseLong(loop.group(1)), loop.group(2).trim());
            }
            if (input.contains("任务列表")) {
                return list();
            }
            if (input.startsWith("取消：") || input.startsWith("取消:")) {
                return cancel(input.substring(3).trim());
            }
            if (input.contains("完成了什么") || input.contains("已触发")) {
                return report();
            }
            Object parsed = Texts.parseLenient(model.chat(List.of(Message.system(SYSTEM), Message.user(input))).trim());
            String answer = parsed instanceof Map<?, ?> map && Json.str(map, "final") != null ? Json.str(map, "final") : String.valueOf(parsed);
            trace.add("final", "讲解回答", "可试用定时、循环、任务列表、取消和回看");
            return answer;
        }

        private String once(long delaySeconds, String content) {
            String id = "job-" + sequence.incrementAndGet();
            ScheduledFuture<?> future = pool.schedule(() -> fire(id, content), delaySeconds, TimeUnit.SECONDS);
            jobs.put(id, new Job(id, "一次性", content, delaySeconds, future, new AtomicInteger()));
            trace.add("action", "一次性注册", id + "：" + delaySeconds + " 秒后「" + content + "」");
            trace.add("thought", "异步语义", "本次调用立即返回，到点结果写入 " + eventFile.getFileName());
            return "已注册一次性任务 " + id + "：" + delaySeconds + " 秒后「" + content + "」。";
        }

        private String recurring(long intervalSeconds, String content) {
            String id = "job-" + sequence.incrementAndGet();
            ScheduledFuture<?> future = pool.scheduleAtFixedRate(
                    () -> fire(id, content), intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
            jobs.put(id, new Job(id, "循环", content, intervalSeconds, future, new AtomicInteger()));
            trace.add("action", "循环注册", id + "：每 " + intervalSeconds + " 秒「" + content + "」");
            return "已注册循环任务 " + id + "：每 " + intervalSeconds + " 秒「" + content + "」。";
        }

        private void fire(String id, String content) {
            Job job = jobs.get(id);
            if (job != null) {
                job.fired().incrementAndGet();
            }
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            synchronized (firedLog) {
                firedLog.add(time + "  [" + id + "] " + content);
            }
            try {
                Files.createDirectories(eventFile.getParent());
                Files.writeString(eventFile,
                        "{\"time\":\"" + time + "\",\"job\":\"" + id + "\",\"content\":\"" + content + "\"}\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {
                // 落盘失败不影响内存里的触发记录
            }
        }

        private String list() {
            StringBuilder body = new StringBuilder("定时任务：\n");
            for (Job job : jobs.values()) {
                String status = job.future().isCancelled() || (job.future().isDone() && "一次性".equals(job.kind()))
                        ? "done/取消"
                        : "循环".equals(job.kind()) ? "循环中 · 已触发 " + job.fired().get() + " 次" : "pending";
                body.append(job.id()).append(" · ").append(job.kind()).append(" · ").append(job.periodSeconds())
                        .append("秒 · 「").append(job.content()).append("」 · ").append(status).append('\n');
            }
            trace.add("observation", "任务列表", jobs.size() + " 个任务在册");
            return body.toString().stripTrailing();
        }

        private String cancel(String id) {
            Job job = jobs.get(id);
            if (job == null) {
                return "没有任务 " + id + "。在册：" + jobs.keySet();
            }
            job.future().cancel(false);
            trace.add("guard", "取消任务", id + " 已停止");
            return "已取消 " + id + "。";
        }

        private String report() {
            List<String> log;
            synchronized (firedLog) {
                log = List.copyOf(firedLog);
            }
            trace.add("observation", "已触发任务汇报", log.isEmpty() ? "暂无触发记录" : log.size() + " 条");
            return "已触发的任务：\n" + (log.isEmpty() ? "（暂无）" : String.join("\n", log));
        }

        @Override
        public void reset() {
            jobs.values().forEach(job -> job.future().cancel(false));
            jobs.clear();
            synchronized (firedLog) {
                firedLog.clear();
            }
        }

        @Override
        public List<Map<String, Object>> trace() {
            return trace.snapshot();
        }
    }
}
