# Function Calling

> 板块：LLM 基础 · 模型自己不会「调用」任何东西，它只会**提议**调用；执行永远是你的程序。

## 协议三步走 {#protocol}

1. **声明**：请求里带上 `tools`，每个工具一份 JSON Schema；
2. **提议**：模型返回 `tool_calls`（工具名 + JSON 参数），而不是普通文本；
3. **回填**：程序执行工具，用 `role: tool` + `tool_call_id` 把结果发回，模型据此继续推理或给出答案。

```json
{
  "type": "function",
  "function": {
    "name": "get_weather",
    "description": "查询城市今天的天气",
    "parameters": {
      "type": "object",
      "properties": { "city": { "type": "string", "description": "城市名" } },
      "required": ["city"]
    }
  }
}
```

## 与 ReAct 文本协议的区别 {#vs-react}

| | 提示词约定 JSON（ToyAgent 03） | 原生 Function Calling（04 / AgentScope） |
| --- | --- | --- |
| 输出格式 | 靠提示词约束，可能跑偏 | 模型经过专门训练，结构稳定 |
| 解析 | 自己去代码围栏、parse | 接口直接给结构化字段 |
| 并行调用 | 一次一个 | 一次可提议多个 |
| 适用 | 不支持工具调用的模型 | 现代主流模型 |

## 工具设计要点 {#tool-design}

- **名字有格式限制**：OpenAI 要求函数名只含字母、数字、下划线和短横线，最长 64 个字符；
- **名字和描述就是提示词**：模型靠 `description` 决定何时调用，写清楚「做什么、什么时候用、返回什么」；
- **参数少而明确**：用 enum、必填项约束，减少模型猜测；
- **结果要可读**：返回模型能理解的简洁文本或 JSON，错误也要返回说明而不是抛异常；
- **区分只读与有副作用**：有副作用的工具要走审批或沙箱（[20 审批门禁](../agent/20-approval-gate)、[21 沙箱](../agent/21-sandbox)）；
- **工具数量有成本**：每个 Schema 都占上下文，工具太多会降低选择准确率 —— 这正是 Skills、MCP 按需加载的动机。

## `tool_choice` {#tool-choice}

| 含义 | OpenAI | Anthropic |
| --- | --- | --- |
| 模型自己决定（有工具时的默认值） | `auto` | `auto` |
| 不调用任何工具 | `none` | `none` |
| 必须调用某个工具 | `required` | `any` |
| 强制调用指定工具 | `{"type": "function", "function": {"name": "..."}}` | `{"type": "tool", "name": "..."}` |

::: warning ⚠️ 推理模型不一定支持强制调用
DeepSeek 思考模式下，`required` 和指定工具会直接返回 400；Claude 最新一代模型（如 Opus 5.5、Sonnet 5.5）也不再支持 `any` / `tool`。这类模型上用 `auto` + 提示词引导，或用 `strict: true` 保证参数合法，要拿结构化结果就用[结构化输出](./05-structured-output)。
:::

其他相关开关：OpenAI 的 `parallel_tool_calls` 控制是否允许一次提议多个调用；工具定义里的 `strict: true` 让参数严格符合 schema（只支持 JSON Schema 的一个子集）。

## 本仓库里的对应 {#in-repo}

- [03 ReAct](../agent/03-react)：用提示词手搓的工具调用协议
- [04 工具调用](../agent/04-tool-call)：原生 Function Calling
- [09 MCP](../agent/09-mcp) / [11 工具注册表](../agent/11-tool-registry)：工具的标准化与动态注册

## 资料 {#links}

- [OpenAI：Function calling](https://platform.openai.com/docs/guides/function-calling)
- [Anthropic：Tool use](https://platform.claude.com/docs/en/agents-and-tools/tool-use/overview)
- [DeepSeek：Tool Calls](https://api-docs.deepseek.com/guides/tool_calls)
