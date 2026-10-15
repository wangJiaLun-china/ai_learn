# Chat API 与流式输出

> 板块：LLM 基础 · 所谓「调用大模型」，就是一次 HTTP POST。

## 请求与响应 {#request}

OpenAI 兼容的 `/chat/completions` 已成事实标准（DeepSeek、通义、智谱、火山方舟、Ollama、vLLM 都兼容）：

```http
POST {base-url}/chat/completions
Authorization: Bearer sk-xxx

{
  "model": "deepseek-chat",
  "messages": [
    {"role": "system", "content": "你是技术助理"},
    {"role": "user", "content": "你好"}
  ],
  "temperature": 0.7
}
```

响应取 `choices[0].message.content`，用量在 `usage` 里。Anthropic Messages API 结构类似，但 system 是独立字段、内容是 content block 数组。

## 消息角色 {#roles}

| 角色 | 谁写的 | 用途 |
| --- | --- | --- |
| `system` | 开发者 | 角色、背景、约束（[02 提示词](../agent/02-prompt)） |
| `user` | 用户 | 本轮输入 |
| `assistant` | 模型 | 历史回复；也可能是 `tool_calls` |
| `tool` | 程序 | 工具执行结果，用 `tool_call_id` 对应调用 |

多轮对话 = 把历史 `user` / `assistant` 按顺序全部带上。**顺序和配对必须合法**：例如每个 `tool_calls` 后面都要跟对应的 `tool` 消息，否则接口报错。

## 流式输出（SSE） {#streaming}

`"stream": true` 时服务端用 Server-Sent Events 逐块推送：

```text
data: {"choices":[{"delta":{"content":"你"}}]}
data: {"choices":[{"delta":{"content":"好"}}]}
data: [DONE]
```

- 首字延迟（TTFT）大幅降低，用户体验好；
- 工具调用参数也是分块到达的，要自己拼接完整 JSON 再解析；
- 框架通常把流包装成事件流 —— AgentScope 的 `streamEvents()` 就是例子（[03 ReAct](../agent/03-react#compare)）。

## 错误与重试 {#errors}

- `429` 限流 / `5xx` 服务端错误：指数退避重试；
- `400` 上下文超长 / 参数非法：重试无用，要裁剪上下文或修请求；
- 超时：设置连接与读取超时，流式场景用「两个分块之间的最大间隔」判断。

## 本仓库里的对应 {#in-repo}

- ToyAgent `llm/OpenAiChatModel.java`：手写的 `/chat/completions` 调用
- ToyAgent `llm/MockChatModel.java`：不配 Key 时的 Mock 模型，所有场景骨架照常运转
- [01 对话](../agent/01-chat)：最小 MVP
