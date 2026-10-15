# 采样参数

> 板块：LLM 基础 · 模型每一步输出的是「下一个 Token 的概率分布」，采样参数决定怎么从分布里挑。

## 生成过程 {#generation}

自回归生成：模型根据已有的全部 Token 预测下一个 Token 的概率分布 → 按采样策略选一个 → 拼回输入 → 重复，直到遇到结束符、停止词或 `max_tokens`。

## 常用参数 {#params}

| 参数 | 作用 | 经验值 |
| --- | --- | --- |
| `temperature` | 拉平或拉尖概率分布。0 ≈ 总选概率最高的，越大越随机 | 工具调用 / 抽取 0~0.3；对话 0.7；创作 0.8~1.0 |
| `top_p` | 核采样：只在累计概率达到 p 的候选里抽 | 一般和 temperature 只调一个 |
| `max_tokens` | 本次最多输出多少 Token | 按任务设上限，防跑飞烧钱 |
| `stop` | 遇到这些字符串立即停止 | 早期 ReAct 用 `Observation:` 截断 |
| `presence_penalty` / `frequency_penalty` | 惩罚重复出现的 Token | 长文本防复读 |
| `seed` | 固定随机种子（部分平台支持） | 便于复现与评估 |

::: info 🚀 Agent 场景怎么选
需要模型**严格遵守格式**（JSON、工具调用、路由分类）时用低温度；需要**发散**（头脑风暴、文案）时用高温度。同一个 Agent 里不同调用可以用不同参数。
:::

## 推理模型的差异 {#reasoning-models}

DeepSeek-R1、OpenAI o 系列、Claude 扩展思考等「推理模型」会先输出一段思考过程（如 `reasoning_content` / thinking block），再给最终答案：

- 部分采样参数（如 temperature）可能被忽略或固定；
- 思考 Token 同样计费，需要单独的预算参数（如 thinking budget）；
- 多轮对话中是否回传思考内容，各家协议规定不同 —— ToyAgentScope 用 `DeepSeekFormatter` 处理这件事（见 [01 对话](../agent/01-chat#compare)）。

## 本仓库里的对应 {#in-repo}

- ToyAgent 的 `OpenAiChatModel` 固定 `temperature = 0.7`
- [03 ReAct](../agent/03-react) / [06 意图路由](../agent/06-intent-router)：依赖模型稳定输出 JSON，生产中应调低温度
