---
step: '21'
---

# 21 沙箱

> 分组：人机协同与安全

**沙箱纵深防御：四道关卡拦住危险命令** —— 不信任模型的输出 —— 它生成的命令照样要过策略、黑名单、边界、规范化四层校验，任何一层拦截即 DENY。

## 核心概念 {#concept}

### 纵深防御（Defense in Depth）

只靠提示词约束模型「别执行危险命令」是脆弱的 —— 提示词注入、模型幻觉都能绕过它。生产级框架 的思路是**层层设防**：即使模型被骗了，命令也出不了沙箱。

① 策略档位 → ② 内容黑名单 → ③ 空间边界 → ④ 路径规范化 → 执行

### 试试这样玩（红蓝对抗）

- 蓝方放行：「执行：ls sandbox」—— 四层 ✓，模拟执行；
- 红方 1：「执行：rm -rf /」—— 第 ② 层拦截（黑名单特征）；
- 红方 2：「执行：cat /etc/passwd」—— 第 ③ 层拦截（越出工作区）；
- 红方 3：「执行：cat sandbox/../../etc/passwd」—— 第 ④ 层拦截（路径逃逸）。

每条轨迹都标注「在哪一层、为什么被拦」—— 拦截器是独立防线，模型再聪明也改变不了判定结果。

## 实现要点 {#impl}

### 四层各拦什么

| 层 | 拦截目标 | 示例 |
| --- | --- | --- |
| ① 策略 | 档位本身不允许 shell | READ_ONLY 档全拒（本场景 WORKSPACE_ONLY） |
| ② 内容 | 破坏性命令特征 | `rm -rf`、`sudo`、`mkfs`、fork 炸弹 `:(){` |
| ③ 边界 | 路径越出工作区 | `cat /etc/passwd` —— 绝对路径不在 sandbox/ 内 |
| ④ 规范化 | 「看似在内、实际在外」 | `sandbox/../../etc/passwd` —— canonicalize 后现形 |

```java
// 第 3 层与第 4 层的分工：看原文 vs 看真实落点
while (abs.find()) {                 // 逐个检查命令里的绝对路径
    if (!workspace.resolve(p).normalize().startsWith(workspace))
        return deny(3, "绝对路径 " + p + " 越出工作区");
}
if (cmd.contains(".."))              // 第 4 层：规范化后复查，../ 逃逸无所遁形
    return deny(4, "检测到 ../ 相对跳转，疑似路径逃逸");
```

::: tip 💡 为什么 ③ 和 ④ 要分两层
`sandbox/../../etc/passwd` 字面上「以 sandbox/ 开头」，第 3 层按原文看是合法的；只有 canonicalize（解析符号链接、压平 ..）之后才暴露真实落点。**两层校验 = 字面检查 + 语义检查**，这是路径安全的标准打法。
:::

## ToyAgent vs ToyAgentScope {#compare}

**沙箱纵深防御：写进工具自己的 checkPermissions** —— 模型每次想调用 shell_execute，框架都会先问工具「这次调用允许吗」；任一层返回 DENY，命令根本不会执行。

① 黑名单 → ② 只读白名单 → ③ 绝对路径边界 → ④ normalize 防穿越

```java
class SandboxShellTool extends ToolBase {
    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> input, PermissionContextState ctx) {
        String cmd = String.valueOf(input.get("command"));
        if (DENYLIST.stream().anyMatch(cmd::contains))      return deny("① 黑名单", ...);
        if (!ALLOW_COMMANDS.contains(head(cmd)))            return deny("② 策略", ...);
        if (hasAbsolutePath(cmd))                           return deny("③ 边界", ...);
        if (!resolve(arg).startsWith(SANDBOX_ABS))          return deny("④ 规范化", ...);
        return Mono.just(PermissionDecision.allow("四层检查通过"));
    }
}
```

「执行：命令」这类确定性命令由代码直接调用 `checkPermissions` 再执行，结果可复现；普通提问交给模型，模型调用时框架同样会走这四层。

::: tip 💡 第五层
生产中可再叠加 HarnessAgent 的 `SandboxFilesystemSpec`（如 `DockerFilesystemSpec`），让 Shell 跑在容器里 —— 规则拦截 + 运行隔离双保险。
:::

## 对应教程 {#chapters}

- 生产级框架 · sandbox / 危险命令拦截器
