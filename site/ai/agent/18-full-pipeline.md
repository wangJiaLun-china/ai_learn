---
step: '18'
---

# 18 全流程

> 分组：运行时与工程化

**全流程智能体：把 17 块积木拼成一台机器** —— 守卫 → 记忆 → ReAct 循环 → 工具 → 保险丝 → 输出校验 → 回写记忆。前面拆开讲的每个零件，这里装回同一台机器，一次 chat() 走完生产级智能体的完整生命周期。

## 核心概念 {#concept}

### 为什么要有一个「全流程」场景

前 17 个场景是**教学拆解**：每次只让你看清一个零件。但真实系统里这些零件是**同时在线**的 —— 守卫在模型之前拦一道，记忆在上下文里垫一层，循环在中间转起来，保险丝在旁边盯着预算。缺一个全流程视角，你就只会造零件，不会装机。

Step18 的 `FullAgent` 把它们全部组装进一个 `chat(String)`。方法签名没变 —— **骨架永远不变，变厚的只是策略层**。

### 执行时序图怎么看

本场景的轨迹会渲染成一张**五参与者时序图**：👤用户、🤖Agent、💾记忆、🧠大模型、🛠工具集。对应关系：

「输入守卫」是 Agent 自己的 `A->>A` 自环；「读取记忆/回写记忆」是 Agent 与记忆的两次读写；「Thought」从大模型流回 Agent；「Action→Observation」是 Agent 与工具集之间的往返；中间若有第二、三轮，就是循环体在转。一张图看完，你就明白生产框架的 trace 为什么长这样 —— 它们只是把同样的信息做成了可观测面板。

## 实现要点 {#impl}

### 完整生命周期：七个环节

```text
用户输入
  │
  ▼
① 输入守卫 ── 长度/敏感词拦截（不进模型，不烧钱）
  ▼
② 读取记忆 ── 历史 + 用户画像，垫进上下文
  ▼
③ 组装上下文 ── system(角色+画像) + 历史 + 本轮输入
  ▼
④ ReAct 循环 ── Thought → Action → Tool → Observation ↻
  ▼                （≤ MAX_STEPS 步）
⑤ 保险丝 ──── 超出步数预算，熔断返回
  ▼
⑥ 输出校验 ── 敏感词替换 + 画像兜底问答
  ▼
⑦ 回写记忆 ── 本轮对话入档；超阈值触发上下文压缩
```

::: tip 💡 灵魂所在
注意①和⑥：**守卫不只管输入，也管输出**。模型是不可靠的（可能被注入、可能胡说），所以工程上它被夹在两道校验中间 —— 输入侧拦恶意请求，输出侧拦违规内容。这就是「模型聪明，运行时可靠」的落地形态。
:::

### 核心代码：守卫 + 记忆 + 循环

下面是 `chat()` 的主干，每个环节一行注释，与前 12 章一一对应：

```java
public String chat(String input) {
    // ① 输入守卫：长度与敏感意图，不通过直接返回，模型预算为零
    if (input.length() > MAX_INPUT_LEN) return "输入过长，请精简后重试";
    if (SENSITIVE.matcher(input).find()) return "该请求已被输入守卫拦截";

    // ② 记忆读取 + 画像提取（我叫 XX → 存档案）
    extractProfile(input);

    // ③ 组装上下文：系统提示词（含画像）+ 历史 + 本轮输入
    List<Message> messages = new ArrayList<>();
    messages.add(Message.system(SYSTEM_PROMPT + userPortrait()));
    messages.addAll(history);
    messages.add(Message.user(input));

    // ④⑤ ReAct 循环 + 保险丝（Step03 的骨架原样复用）
    String answer = runReAct(messages);

    // ⑥ 输出校验 + 画像兜底（「我叫什么」不问模型，查档案）
    answer = outputGuard(answer, input);

    // ⑦ 回写记忆，超阈值触发上下文压缩
    history.add(Message.user(input));
    history.add(Message.assistant(answer));
    if (history.size() > COMPRESS_THRESHOLD) compressHistory();
    return answer;
}
```

::: info 🧩 复用而非重写
`runReAct()` 与 Step03 的循环一字不差；`compressHistory()` 是 Step05 压缩思想的简化版；守卫规则来自 Step15。全流程场景没有发明新东西 —— 它的价值在于**组装顺序**：守卫在最前，记忆在两侧，循环在核心，保险丝贴着循环。
:::

### 画像兜底：一条容易被忽略的细节

用户问「我叫什么名字」时，这条信息可能在上下文压缩时被裁掉了。此时直接问模型必然失忆。正确做法是**画像单独持久化**，问答时先查档案：

```java
if (input.contains("我叫什么")) {
    String name = profile.get("名字");
    if (name != null) return "你叫 " + name + "（来自画像记忆，不依赖模型）";
}
```

这就是为什么记忆要分两层：**对话历史**（易失、会压缩）与**用户画像**（持久、结构化）。生产系统的记忆组件（mem0、LangGraph Store）都遵循这个分层。

### 距离生产还差什么

坦率列出这份「玩具」与工业级 Harness 的差距，每一条都是明确的学习路标：

```text
1. 流式输出   → chat() 返回 String，生产要 Stream（SSE 边生成边吐字）
2. 并发会话   → 记忆是实例字段，生产要按 sessionId 隔离 + 持久化
3. 真实工具   → switch 执行器 → MCP 网关 / 插件市场，带超时重试
4. 上下文预算  → 按条数压缩 → 按 Token 计费裁剪 + 重要度打分
5. 可观测性   → trace 内存列表 → 结构化日志 + trace 面板 + 告警
6. 评测回归   → 无 → 用例集 + LLM-as-Judge，改提示词要跑回归
```

::: warning 🗺 进阶路径
消化完这个场景，可以继续拓宽到 RAG / CLI Agent / GUI Agent 等实战方向，再去看成熟框架里这些环节的工业级写法。
:::

## ToyAgent vs ToyAgentScope {#compare}

**全流程智能体：把积木拼成一条链路** —— 输入守卫 → 画像抽取 → HarnessAgent（onSystemPrompt 注入画像 + ReAct + 工具 + 压缩记忆）→ 输出守卫 → 记忆回写。

输入守卫 → 画像抽取 → onSystemPrompt 注入 → ReAct + 工具 → maxIters → 输出守卫 → 记忆回写

```java
HarnessAgent.builder()
        .model(model).toolkit(toolkit)
        .maxIters(5)
        .middleware(new ProfileMiddleware())          // onSystemPrompt：追加【用户画像】
        .compaction(CompactionConfig.builder().triggerMessages(12).keepMessages(6).build())
        .disableFilesystemTools().disableShellTool()...
        .build();

private class ProfileMiddleware implements MiddlewareBase {
    public Mono<String> onSystemPrompt(Agent a, RuntimeContext c, String current) {
        return Mono.just(profile.isEmpty() ? current : current + "

【用户画像】" + profileText());
    }
}
```

::: tip 💡 HarnessAgent = ReActAgent 全家桶
HarnessAgent 在 ReActAgent 外面加了工作区（AGENTS.md / MEMORY.md / skills/）、文件与 Shell 工具、长期记忆、子代理、计划模式、会话持久化等。教学场景按需 `disableXxx()` 关掉，只保留要演示的能力；`getDelegate()` 可拿到内部的 ReActAgent。
:::

## 对应教程 {#chapters}

- 综合 Step01-17
- ch18 Agent Loop 运行时
- ch21 工业级 Harness
