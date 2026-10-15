---
step: '20'
---

# 20 审批门禁

> 分组：人机协同与安全

**审批门禁：高危操作先过人** —— 模型可以决定「做什么」，但「能不能做」要过权限矩阵 —— 参考运行期审批链路：风险分级、审批挂起、会话免审、超时默认拒绝。

## 核心概念 {#concept}

### 为什么模型不能想干嘛就干嘛

让智能体发邮件、删文件、执行命令 —— 能力越强，误操作（或被诱导）的代价越大。生产级框架的答案是一道**运行期审批链路**：工具按风险分级，高危动作必须等人点头。

### 四个设计点

| 设计点 | 本场景实现 | 生产级框架 对应 |
| --- | --- | --- |
| 风险分级 | get_weather=安全只读；send_email / delete_file=高危写操作 | 提交期权限矩阵：Shell/写文件/插件默认需人工审批 |
| 三档模式 | `Mode.AUTO_MANUAL_SAFE / ALWAYS_ASK / FULL_AUTO` | 宽松/标准/严格三档策略 |
| 会话免审 | 「批准并记住」→ 本会话同工具自动放行 | 会话级免审记忆：信任一次，不再打扰 |
| 超时默认 DENY | 轨迹提示（教学为同步架构） | 审批超时未响应 → 自动拒绝，宁可不执行 |

### 试试这样玩

- 发「给团队发一封周报邮件」—— 轨迹：权限矩阵评估 → 高危 → 审批挂起；
- 回复「批准」—— 放行执行，下次仍会问；回复「拒绝」—— 拦截，任务终止；
- 回复「批准并记住」—— 之后同类高危操作自动放行（看轨迹里的「会话免审」标记）；
- 发「查一下北京天气」—— 安全工具直接放行，全程无审批打扰。

## 实现要点 {#impl}

### 核心代码：一条挂起-决策链

```java
boolean needAsk = mode == Mode.ALWAYS_ASK
        || (mode == Mode.AUTO_MANUAL_SAFE && dangerous && !trustedTools.contains(tool));

if (needAsk) {
    pendingTool = tool;              // 审批挂起：先答复人，不执行
    return "⚠️ 即将执行高危操作：" + tool + "…… 回复「批准 / 拒绝 / 批准并记住」";
}
// 人回复后进入 resolveApproval：批准→执行；拒绝→终止；批准并记住→加入免审名单
```

::: tip 💡 设计哲学
**模型的决策权与人的一票否决权分离** —— 模型说「我要发邮件」，门禁说「你先等等」。拒绝后操作**根本没发生**，而不是发生后再回滚。超时默认 DENY 而非 ALLOW，是把不确定性的代价留给可用性而非安全。
:::

## ToyAgent vs ToyAgentScope {#compare}

**审批门禁：AgentScope 权限系统** —— 只读工具配 ALLOW 白名单，其余工具默认 ASK；模型调用高危工具时框架挂起（PERMISSION_ASKING），下一条消息携带 ConfirmResult 决定放行或拒绝。

### 权限引擎的判定顺序（2.0.4 源码）

① DENY 规则 → ② ASK 规则 → ③ 工具自检 checkPermissions → ④ ALLOW 规则 → ⑤ BYPASS 模式 → ⑥ 默认 ASK

```java
// 风险分级：白名单放行，其余默认询问
PermissionContextState.Builder b = PermissionContextState.builder().mode(PermissionMode.DEFAULT);
for (String t : List.of("get_weather", "calculator", "get_time")) {
    b.addAllowRule(t, new PermissionRule(t, null, PermissionBehavior.ALLOW, "policy"));
}
ReActAgent.builder()...permissionContext(b.build()).build();

// 挂起后：用户的决定放进下一条消息的 metadata
List<ConfirmResult> results = pending.stream().map(call -> remember
        ? new ConfirmResult(true, call, List.of(new PermissionRule(call.getName(), null, PermissionBehavior.ALLOW, "session")))
        : new ConfirmResult(approve, call)).toList();
Msg resume = Msg.builder().role(MsgRole.USER).textContent("批准")
        .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results)).build();
```

::: warning ⚠️ 实测踩坑：「批准并记住」不生效
第一版给高危工具显式配了 ASK 规则，结果「批准并记住」后第二封邮件仍要审批。看源码才明白：**ASK 规则（②）优先级高于 ALLOW 规则（④）**，会话追加的 ALLOW 永远压不过它。改成「只配 ALLOW 白名单、其余走默认 ASK（⑥）」后，会话免审立即生效。
:::

::: tip 💡 超时默认 DENY
挂起超过 60 秒才答复，一律按拒绝处理（fail-safe）。人不在场时，宁可不做也不能误做。
:::

## 对应教程 {#chapters}

- 生产级框架 · approval / 权限矩阵
