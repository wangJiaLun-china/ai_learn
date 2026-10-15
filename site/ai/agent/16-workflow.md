---
step: '16'
---

# 16 工作流

> 分组：运行时与工程化

**工作流状态机：LangGraph 的确定性编排** —— ReAct 是模型说了算，工作流是流程说了算。节点 + 条件边 + 共享状态，可控性与灵活性的平衡点。

## 核心概念 {#concept}

### ReAct 的另一面

ReAct 把流程决定权完全交给模型 —— 灵活，但业务方睡不着觉：模型今天绕路明天发散，审计与合规无从谈起。客服、工单、审批这类场景，需要**流程说了算**。

### 执行路径可视化

classify → tool / faq（条件边） → polish → END

::: tip 💡 混合范式（生产主流）
工作流管骨架（确定性），节点内部跑 ReAct（灵活性）。例如「客服工单流」：分类节点 → 处理节点（内部 ReAct 自由调工具）→ 合规节点 → 结案。**宏观可控、微观灵活**。
:::

### Checkpoint 与中断恢复

LangGraph 的招牌能力：每个节点执行后把 State 落盘（Checkpoint）。流程任意位置可暂停、人工审批后继续（Human-in-the-loop）、失败后从最近检查点重放 —— 这在纯 ReAct 循环里几乎做不到，因为 ReAct 的状态埋在对话历史里，而**状态机的状态是显式的、可序列化的**。

### 课程收官

走到这里，回看总览的公式：**智能体 = 大模型 + 工具 + 记忆 + 循环 + 工程化**。12 个场景把每一项都拆开给你看过了。接下来建议：把某个场景的工具换成真实 API、把 Mock 模型换成真实 Key，跑通一条属于你自己的链路 —— 智能体开发的所有进阶，都是在这个骨架上加装饰。

## 实现要点 {#impl}

### 图编排三要素

| 要素 | 含义 | 本场景 |
| --- | --- | --- |
| 节点 Node | 一段能力（模型调用/工具/纯逻辑） | classify / tool / faq / polish |
| 边 Edge | 固定流转 + 条件路由 | classify --next--> tool 或 faq |
| 状态 State | 节点间共享的数据通道 | Map&lt;String,Object&gt; state |

```java
// State：在节点之间流转的共享状态
Map<String, Object> state = new LinkedHashMap<>();
state.put("input", input);

// 固定入口
runNode("classify", state);

// 条件边：按分类结果路由
String next = (String) state.get("next");
runNode(next, state);

// 汇合点：统一润色后结束
runNode("polish", state);
```

## ToyAgent vs ToyAgentScope {#compare}

**工作流状态机：图是代码，节点是框架积木** —— classify（结构化输出）→ tool（callTool）| faq（agent）→ polish（agent）→ END。

```text
classify ──tool──▶ tool ──┐
    └─────faq────▶ faq  ──┴─▶ polish ─▶ END
```

```java
record Route(String next, String toolName, String argument) {}

Route route = Texts.structured(classifier.call(msgs, Route.class, ctx()).block(), Route.class);
String next = "tool".equalsIgnoreCase(route.next()) ? "tool" : "faq";      // 条件边
String material = next.equals("tool")
        ? ToolCalls.callText(toolkit, route.toolName(), args)              // tool 节点：不经模型直接调工具
        : text(faq, input);                                                // faq 节点
return text(polisher, "用户问题：" + input + "
素材：" + material);        // 汇合节点
```

::: tip 💡 智能体 vs 工作流
Step03 由**模型**决定下一步；工作流由**开发者**画好图，模型只在节点内干活 —— 更可控、更可测。AgentScope 2.0 没有内置图引擎，显式状态机就是普通 Java 代码。
:::

## 对应教程 {#chapters}

- ch11b LangGraph 与状态机
- ch15 图编排
