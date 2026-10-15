# 采样参数

> 板块：LLM 基础 · 模型每一步输出的是「下一个 Token 的概率分布」，采样参数决定怎么从分布里挑。

## 生成过程 {#generation}

自回归生成：模型根据已有的全部 Token 预测下一个 Token 的概率分布 → 按采样策略选一个 → 拼回输入 → 重复，直到遇到结束符、停止词或输出上限。

## 常用参数 {#params}

以 OpenAI Chat Completions 的定义为准（DeepSeek 等兼容接口基本沿用）：

| 参数 | 作用 | 取值 / 经验 |
| --- | --- | --- |
| `temperature` | 拉平或拉尖概率分布。越低越确定，越高越随机 | 0~2，默认 1。工具调用 / 抽取用低值；创作用高值 |
| `top_p` | 核采样：只在累计概率达到 p 的候选里抽 | 0~1，默认 1。官方建议和 temperature 只调一个 |
| `max_completion_tokens` | 本次最多生成多少 Token，**含推理 Token** | OpenAI 已用它取代旧的 `max_tokens`（推理模型不兼容旧参数）；DeepSeek 等仍用 `max_tokens` |
| `stop` | 生成到这些字符串时停止，返回内容不含停止词 | OpenAI 最多 4 个；最新推理模型不支持 |
| `presence_penalty` / `frequency_penalty` | 惩罚已出现 / 高频出现的 Token，减少复读 | -2~2，默认 0 |
| `seed` | 尽力做到同参数同结果 | Beta 且已标记弃用，**不保证确定性** |

::: info 🚀 Agent 场景怎么选
需要模型**严格遵守格式**（JSON、工具调用、路由分类）时用低温度；需要**发散**（头脑风暴、文案）时用高温度。同一个 Agent 里不同调用可以用不同参数。
:::

## 推理模型：采样参数让位于「思考强度」 {#reasoning-models}

推理模型会先产生一段思考过程再给答案，各家都把控制重点从采样参数换成了**思考强度（effort）**：

| 平台 | 思考开关 / 强度 | 采样参数 | 思考内容 |
| --- | --- | --- | --- |
| OpenAI 推理模型 | `reasoning_effort` | 部分参数不支持（如 `stop`）；旧的 `max_tokens` 不兼容 | 思考 Token 计入 `max_completion_tokens` |
| DeepSeek | `thinking: {"type": "enabled"/"disabled"}` + `reasoning_effort`（low / high / max，默认 high） | 思考模式下 `temperature`、`presence_penalty`、`frequency_penalty` 传了不报错但**无效** | 通过 `reasoning_content` 返回；带工具调用时要回传，不带工具时回传也会被忽略 |
| Anthropic Claude | `output_config.effort`；新模型用自适应思考（adaptive），已不再使用固定的思考 Token 预算 | 最新模型不再接受非默认的 `temperature` / `top_p` | 以 thinking 块返回，同模型续聊时原样回传 |

::: warning ⚠️ 迁移时注意
老代码里固定的 `temperature: 0.7`、`budget_tokens` 之类参数，换到新推理模型上可能被忽略，也可能直接报 400。切换模型前先查对应平台的参数说明。ToyAgentScope 用 `DeepSeekFormatter` 处理 `reasoning_content` 的回传（见 [01 对话](../agent/01-chat#compare)）。
:::

## 本仓库里的对应 {#in-repo}

- ToyAgent 的 `OpenAiChatModel` 固定 `temperature = 0.7`（对 DeepSeek 思考模式无效）
- [03 ReAct](../agent/03-react) / [06 意图路由](../agent/06-intent-router)：依赖模型稳定输出 JSON，非推理模型应调低温度

## 资料 {#links}

- [OpenAI Chat Completions API 参考](https://platform.openai.com/docs/api-reference/chat/create)
- [DeepSeek：思考模式](https://api-docs.deepseek.com/guides/thinking_mode)
- [Anthropic：Extended thinking](https://platform.claude.com/docs/en/build-with-claude/extended-thinking)
