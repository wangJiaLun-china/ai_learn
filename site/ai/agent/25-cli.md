---
step: '25'
---

# 25 CLI

> 分组：扩展与形态

**CLI 智能体：把骨架装进终端** —— 形态变了，灵魂不变 —— 仍是 Agent.chat(input)：外面套一层终端 REPL，工具侧换成沙箱执行器。

## 核心概念 {#concept}

### CLI Agent 是什么

Claude Code、Aider、Gemini CLI…… 这一形态的共同点：**以终端为界面、以命令为手脚、以对话为交互**。剥掉外壳，骨架和 Web 版完全一样。

::: tip 💡 形态即壳
REPL 只是 Agent.chat 的 while 循环；单命令 -p 模式只是调用一次就退出。**换形态不动骨架**，这正是「一个接口类」设计的回报。
:::

### 本场景的三个要点

| 要点 | 实现 | 呼应 |
| --- | --- | --- |
| shell 执行器 | `run_command`：ProcessBuilder 按空格拆分（不经 shell，无管道/重定向注入面） | Step04 工具调用 |
| 四层沙箱 | 拒绝名单 → 路径边界 → 命令白名单 → 受限工作区 cwd | **直接复用 Step21** |
| 工作区上下文 | cwd 进入系统提示词，像终端会话一样「知道自己在哪」 | Step02 提示词 |

### 试试这样玩

- 「运行：ls」「运行：cat hello.txt」—— 沙箱内执行，轨迹展示退出码与 cwd；
- 「运行：rm -rf /」→ 第①层拒绝名单拦截；「运行：cat /etc/passwd」→ 第②层路径边界拦截；
- 「列出工作区文件」—— 不写死命令，看模型自己决策调用 run_command。

生产化路径：命令拆分换 shlex 级解析、白名单升级为权限配置、输出截断与超时控制、审批接入（高危命令过 Step20 审批门禁）。

## 实现要点 {#impl}

### REPL 骨架（Java 侧）

```java
// 终端形态 = Agent.chat 的 while 循环（完整实现见 CliAgent.java）
BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
while (true) {
    System.out.print("you› ");
    String line = in.readLine();
    if (line == null || line.equalsIgnoreCase("exit")) break;
    if (line.isBlank()) continue;
    System.out.println("agent› " + agent.chat(line));   // 灵魂不变
}

// 手脚换成沙箱执行器：ProcessBuilder 按空格拆分，不经 shell
// （无管道/重定向注入面），四层防御复用 Step21
ProcessBuilder pb = new ProcessBuilder(command.split("\s+"));
pb.directory(workdir);   // 受限工作区 = 沙箱第④层
Process proc = pb.start();
```

### 终端入口（零依赖可跑）

```bash
# 交互式 REPL
java -cp target/classes com.wangjialun.ai.toyagent.agent.step25.CliAgent

# 非交互单命令（Claude Code 同款 -p 习惯）
java -cp target/classes com.wangjialun.ai.toyagent.agent.step25.CliAgent -p "看看工作区里有什么"
```

## ToyAgent vs ToyAgentScope {#compare}

**CLI 智能体：同一个智能体，换一个壳** —— Web 里是 /api/step25/chat，终端里是 REPL 与 -p 单命令模式；run_command 用 checkPermissions 三道检查后真实执行。

```bash
# REPL
./gradlew -q runCli

# 单命令模式（Claude Code 风格）
./gradlew -q runCli --args='-p 运行：ls'
java -cp build/libs/ToyAgentScope.jar com.wangjialun.ai.toyagentscope.agent.step25.CliAgent -p "列出工作区文件"
```

```java
class RunCommandTool extends ToolBase {
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> in, PermissionContextState ctx) {
        // 黑名单（rm/sudo/管道/重定向…）→ 路径边界（.. 与绝对路径）→ 白名单（ls/pwd/echo/cat/date/wc）
    }
    public Mono<ToolResultBlock> callAsync(ToolCallParam p) {
        // ProcessBuilder 真实执行：工作目录 data/sandbox/step25，5 秒超时
    }
}
```

::: tip 💡 形态即壳
智能体的内核（模型 + 工具 + 权限 + 循环）不变，Web、CLI、IM 机器人只是不同的输入输出壳。HarnessAgent 的 Channel（ChatUi / 钉钉 / 飞书…）就是把这层壳标准化。
:::

## 对应教程 {#chapters}

- ch18 CLI Agent：命令行智能助手
