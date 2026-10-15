# 结构化输出

> 板块：LLM 基础 · 程序要的是字段，不是散文。让模型稳定吐出可解析的 JSON，是 Agent 工程的日常。

## 三种做法 {#approaches}

| 做法 | 原理 | 稳定性 |
| --- | --- | --- |
| 提示词约束 | 「只输出 JSON，格式如下……」+ 示例 | 最弱，可能夹带解释或代码围栏 |
| JSON Mode | `response_format: {"type": "json_object"}`，保证是合法 JSON | 语法有保证，字段不保证 |
| Schema 约束 | `json_schema` 严格模式，或把 schema 声明成一个工具并强制调用 | 最强，字段和类型都有保证 |

::: info 🚀 用工具调用做结构化输出
很多框架的通用做法：把目标结构定义成一个「工具」，用 `tool_choice` 强制模型调用它，工具参数就是你要的结构。ToyAgentScope 配置了 `nativeStructuredOutput(false)` 走的就是这条路（见 [06 意图路由](../agent/06-intent-router#compare)）。
:::

## 解析兜底 {#parsing}

即使用了强约束，生产代码也要防御：

1. 去掉 ```` ```json ```` 代码围栏和前后多余文本；
2. 解析失败时把错误信息回传给模型重试（限次数）；
3. 校验字段与枚举值，非法值走默认分支；
4. 记录原始输出，便于排查。

## 典型用途 {#use-cases}

- **意图分类 / 路由**：输出 `{"intent": "weather", "confidence": 0.9}`（[06 意图路由](../agent/06-intent-router)）
- **信息抽取**：从文本中抽出实体、字段
- **规划**：输出步骤列表交给执行器（[12 多智能体](../agent/12-multi-agent)）
- **ReAct 文本协议**：每轮输出 thought / action / final（[03 ReAct](../agent/03-react)）
