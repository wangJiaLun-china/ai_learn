---
step: '07'
---

# 07 RAG

> 分组：记忆与知识

**RAG：戴着资料说话** —— 模型不知道你的私有知识。检索找资料，提示词喂资料，生成有出处 —— 三步缓解幻觉。

## 核心概念 {#concept}

### RAG 解决什么问题

- **知识过期**：模型训练截止后的事情它不知道；
- **私有知识**：你公司文档、本项目细节，模型从没见过；
- **幻觉**：不知道时模型会编 —— RAG 强制它「戴着资料说话」。

### 教学版相似度 vs 生产级检索

|   | 本场景 | 生产实现 |
| --- | --- | --- |
| 相似度 | 字符 bigram 重合率 | Embedding 余弦相似度 |
| 存储 | 内存 List | 向量库（Milvus / ES / pgvector） |
| 预处理 | 手工知识条目 | 文档切分 → 向量化 → 入库 |
| 进阶 | — | 重排序、HyDE、GraphRAG、Agentic RAG |

::: info ✅ 骨架不变论
把 `score()` 换成 Embedding 相似度、把 List 换成向量库，本场景立刻变成生产级 RAG —— **三步骨架（检索→增强→生成）永不过时**。
:::

### 拒答也是能力

试试问「量子力学的泡利不相容原理」—— 知识库没命中时，模型按约定如实拒答，而不是硬编。**允许模型说「不知道」，是 RAG 工程里最容易被忽略、却最重要的设计**。

## 实现要点 {#impl}

### 三步骨架

1. 检索 Retrieve → 2. 增强 Augment → 3. 生成 Generate

```java
// 1. 检索：知识片段与问题打分，取 Top2
List<Hit> hits = KNOWLEDGE.stream()
        .map(text -> new Hit(text, score(text, input)))
        .sorted(Comparator.comparingDouble(Hit::score).reversed())
        .limit(2).toList();

// 2. 增强：命中片段注入提示词，带来源编号
StringBuilder ctx = new StringBuilder("【参考资料】\n");
for (int i = 0; i < hits.size(); i++) {
    ctx.append("[").append(i + 1).append("] ")
       .append(hits.get(i).text()).append("\n");
}

// 3. 生成：约束模型"优先依据资料回答，不足则拒答"
String answer = model.chat(List.of(
        Message.system("优先依据【参考资料】回答并标注来源 [1]；"
                + "资料不足时如实说不知道，禁止编造。"),
        Message.user(ctx.toString()),
        Message.user(input)));
```

## ToyAgent vs ToyAgentScope {#compare}

**RAG：把检索做成工具（Agentic RAG）** —— 模型自己决定查不查、用什么词查、查几次；检索结果作为工具结果回到上下文。

### Generic RAG vs Agentic RAG

|   | Generic（手写版） | Agentic（本场景） |
| --- | --- | --- |
| 何时检索 | 每次都先检索 | 模型判断需要时调用工具 |
| 检索词 | 原问题 | 模型改写后的关键词，可多次 |
| 注入方式 | 拼进提示词 | ToolResultBlock 回填 |

```java
class KnowledgeTools {
    @Tool(name = "search_knowledge", description = "在私有知识库中检索相关片段，返回带编号的 Top2", readOnly = true)
    public String search(@ToolParam(name = "query", description = "检索关键词") String query) {
        // 教学：字符二元组重合度模拟向量相似度；生产换 Embedding + 向量库
        ...
    }
}
// Tracer 把该工具的结果标成 retrieve 节点
Tracer.of(log).observationType(t -> "search_knowledge".equals(t) ? "retrieve" : "observation")
```

::: tip 💡 2.0 的 RAG 现状
AgentScope 1.x 的 `Knowledge` / `RAGMode` 在 2.0 中仍可用但已标记 *deprecated for removal*，官方正在重写；扩展包 `agentscope-extensions-rag-simple / bailian / dify / ragflow` 提供现成实现。把检索包成 @Tool 是最不受 API 变动影响的写法。
:::

::: info 🧪 试一试
问「量子力学的泡利不相容原理是什么？」—— 模型会换几个关键词反复检索，确认知识库没有后如实拒答。
:::

## 对应教程 {#chapters}

- ch16 RAG
- ch20 检索增强生成
