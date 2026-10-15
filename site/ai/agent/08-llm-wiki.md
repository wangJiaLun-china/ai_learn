---
step: '08'
---

# 08 LLM-Wiki

> 分组：记忆与知识

**LLM-Wiki：知识编译与持久化** —— RAG 每次提问都从头翻文档；LLM-Wiki 把项目知识编译成条目、持久维护 —— 编译一次，持续复用。

## 核心概念 {#concept}

### RAG 之外的另一种知识

教程第 13 章（LLM-Wiki，源自 Andrej Karpathy 的提法）区分了两类知识：

- **检索型知识** —— 文档、手册、网页，量大且杂，适合 **RAG**（场景 07）每次检索；
- **项目知识** —— 架构约定、代码规范、部署流程、业务规则。它不该每次都"翻箱倒柜"，而应**编译成结构化条目，随启动直接加载**。

::: tip 💡 一句话区分
RAG：知识躺着，问一次查一次；LLM-Wiki：知识被**编译一次、持久保存、增量维护**。AGENTS.md / CLAUDE.md 就是它的工程形态。
:::

### 和 RAG 场景的三个不同

| 维度 | 09 · RAG | 14 · LLM-Wiki |
| --- | --- | --- |
| 知识来源 | 静态文档集合，只读 | 内置条目 + 对话中**增量编译** |
| 生命周期 | 每次提问重新检索 | **持久化**（wiki/user-wiki.md），启动即恢复 |
| 典型问题 | "这份文档里说了什么" | "这个项目的约定是什么" |

### 试试这样玩

- 先问「项目用什么代码规范？」—— 命中内置条目，回答带来源；
- 再说「编译：发布流程：每周三灰度，周五全量上线」—— 新条目持久化；
- 接着问「发布流程是什么？」—— 刚编译的知识立刻可用；点「重置」清空编译条目。

生产化路径：条目存仓库（AGENTS.md）、bigram 换 Embedding、编译动作交给 Agent 自主维护（自我更新 Wiki）。

## 实现要点 {#impl}

### 本场景的骨架

1. 加载 Wiki → 2. 命中条目 → 3. 增强生成

```java
// 基础 Wiki：启动时编译一次（生产中来自仓库里的 AGENTS.md）
private static final Map<String, String> BASE_WIKI = Map.of(
    "架构约定", "ToyAgent 是零依赖教学项目……",
    "代码规范", "Java 17 语法；中文 Javadoc……",
    "部署流程", "javac 编译后启动，默认端口 8099……");

// 编译指令：把对话中的知识沉淀进 Wiki 并持久化
if (input.startsWith("编译：")) {
    String topic = ..., content = ...;
    userWiki.put(topic, content);   // 增量条目
    persistUserWiki();              // 写入 wiki/user-wiki.md，重启不丢
}
```

## ToyAgent vs ToyAgentScope {#compare}

**LLM-Wiki：编译一次，持久维护** —— 两个工具：wiki_search 查条目，wiki_compile 把新知识沉淀进 Markdown 文件。由模型判断用户是在提问还是要它记住。

```java
@Tool(name = "wiki_compile", description = "把一条新知识编译进项目 Wiki 并持久化；用户说「编译：主题：内容」或「记住…」时调用")
public String compile(@ToolParam(name = "topic", description = "条目主题") String topic,
                      @ToolParam(name = "content", description = "条目内容") String content) {
    WikiStore.compile(topic, content);           // 写 data/wiki/user-wiki.md
    return "已编译条目【" + topic + "】";
}
```

与手写版的差别：手写版用前缀「编译：」做硬路由；框架版把两个能力都交给模型，自然语言「帮我记一下发布流程…」也能触发沉淀。

::: tip 💡 与 HarnessAgent 的工作区记忆对照
HarnessAgent 的 workspace 里，`AGENTS.md` 常驻上下文、`MEMORY.md` 由记忆钩子定期整合每日笔记 —— 那是框架内置的「LLM-Wiki」。本场景用两个 @Tool 手搓同样的机制，便于看清原理。
:::

## 对应教程 {#chapters}

- ch13 LLM-Wiki
- ch26 知识编译与持久化
