---
step: '24'
---

# 24 Hooks

> 分组：扩展与形态

**Hooks 钩子：挂上去就生效的能力拦截点** —— 工具执行前后不是裸奔的 —— 宿主在 before / after 两个生命周期点暴露钩子位，审计、风控、脱敏都从这里挂进去，工具代码一行不改。

## 核心概念 {#concept}

### 为什么需要钩子

审计、脱敏、风控这些需求有一个共同点：**横切在所有工具上**。写进每个工具？改一次规则要改 N 处。钩子把它们抽出来挂在生命周期上 —— **挂钩即生效，摘钩即消失**，工具作者对此无感知。

::: tip 💡 一句话理解
钩子 = 工具调用链上的 AOP。生产级框架 的 HookService 管挂载与触发，HookMatcher 管「挂在哪类工具上」，HookOutputMerger 管「多条 after 钩子按序合并输出」。
:::

### 两个挂载点，三种干预方式

| 挂载点 | 时机 | 能做什么 | 本场景的钩子 |
| --- | --- | --- | --- |
| **before** | 工具执行前 | 拦截（DENY）/ 改写参数 / 只记录 | 审计（全工具记录）、风控（短信含「密码/验证码」即拒） |
| **after** | 工具返回后 | 改写输出 / 脱敏 / 追加告警 | 脱敏（手机号 138*********、邮箱打码） |

匹配规则（HookMatcher）：每条钩子声明自己挂在哪些工具上，「*」= 全部；enabled=false 时整条链路直通 —— 这就是「钩子：关」实验的原理。

### 试试这样玩（做对照实验）

- 「查询：张三」—— 输出里手机号/邮箱已打码（after 脱敏生效）；
- 「发送：给 13812345678 发 密码是123456」—— before 风控直接拦截，工具根本没执行；
- 「钩子：关」→ 再「查询：张三」—— 明文裸奔，对比出钩子的价值；「审计日志」看 before 记录了什么。

生产化路径：钩子声明为 SPI 插件热插拔、审计落 SIEM、脱敏规则走正则库 + 人工审核、钩子链短路策略可配置。

## 实现要点 {#impl}

### 本场景的骨架

工具调用请求 → before 链（审计→风控） → 工具执行 → after 链（脱敏）

```java
// 钩子本体：挂载点 + 匹配规则 + 处理器
// handler 返回 null=放行；"DENY:原因"=拦截；否则替换内容
record Hook(String name, String phase, List<String> match, Handler handler) {}

hooks.add(new Hook("风控-敏感外发", "before", List.of("send_sms"), (tool, payload) ->
    payload.contains("密码") ? "DENY:敏感信息禁止外发" : null));

hooks.add(new Hook("脱敏", "after", List.of("*"), (tool, payload) ->
    Pattern.compile("(1[3-9])\d{9}").matcher(payload).replaceAll("$1*********")));

// 管线：before 任何一条拦截即终止；after 按注册顺序依次加工（HookOutputMerger）
```

## ToyAgent vs ToyAgentScope {#compare}

**Hooks：用工具装饰器实现 before / after** —— HookedTool 包住真实工具：before 钩子可拦截（审计、风控），after 钩子可改写结果（脱敏）。模型调用、代码直调都会经过钩子。

```java
class HookedTool implements AgentTool {          // AgentScope 的工具 SPI
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        for (Hook h : before) {
            String block = h.before().apply(param.getInput());
            if (block != null) return Mono.just(ToolResultBlock.error("被钩子拦截：" + block));
        }
        return delegate.callAsync(param).map(r -> {
            String text = Texts.textOf(r);
            for (Hook h : after) text = h.after().apply(text);   // 脱敏
            return ToolResultBlock.text(text);
        });
    }
}
raw.getToolNames().forEach(n -> toolkit.registerAgentTool(new HookedTool(raw.getTool(n), this)));
```

### 为什么不用 Middleware？

读 2.0.4 源码可知：`onActing` 拿到的是 `ActingInput(toolCalls)`，能改写参数；但工具结果由框架内部的 resultHolder 收集后写回上下文，中间件改写事件流**不会**改变模型看到的结果。旧版 `Hook(PreActingEvent/PostActingEvent)` 能改结果，但已标记弃用。所以「按工具拦截 + 改写结果」用工具装饰器最直接；Middleware 更适合横切整个推理（见 Step17 / Step18）。

::: info 🧪 试一试
「查询：张三」看脱敏 → 「发送：给 13812345678 发 密码是123456」看风控拦截 → 「钩子：关」再查询，手机号裸奔 → 「审计日志」。
:::

## 对应教程 {#chapters}

- 生产级框架 · HookService / HookMatcher / HookOutputMerger
