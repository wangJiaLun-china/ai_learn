---
step: '10'
---

# 10 Skills

> 分组：工具的进化

**Skills：从单步工具到可复用的能力包** —— 工具解决一步的问题，技能解决一类的问题。L0 提示词、L1 工具组合、L2 子流程，三层沉淀。

## 核心概念 {#concept}

### 为什么有了工具还要技能

「做一份杭州旅行攻略」需要：查天气 + 看时间 + 结合日期给建议 + 排版输出。让模型每次现场思考这个流程，既慢又不稳定。**把验证过的流程封装起来，就是技能（Skill）**。

### 技能的三层抽象

| 层级 | 构成 | 例子 |
| --- | --- | --- |
| **L0** 提示词技能 | 纯 Prompt 模板 | 「翻译腔修正器」「周报生成器」 |
| **L1** 工具组合 | 固定调用一组工具 + 拼装 | 旅行攻略 = 天气 + 时间 + 模板 |
| **L2** 子流程 | 一个完整 ReAct 循环封成技能 | 「代码审查技能」内部跑多轮工具链 |

### 技能的沉淀机制

Claude Skills、OpenAI GPTs、企业插件市场，本质都是同一件事：**把「个人/组织经验」固化为可复用资产**。一个好的技能包 = 描述文档（供路由匹配）+ 执行逻辑（工具编排）+ 示例（Few-shot）。技能库越厚，智能体越强，而模型不用换。

## 实现要点 {#impl}

### 先选技能，再执行技能

```java
// 1. 技能选择：模型按语义匹配
String skill = Json.str(model.chat(...), "skill");

// 2. 技能执行：确定性编排，而非现场发挥
return switch (skill) {
    case "travel_plan"    -> travelPlan(input);  // L1: 组合两个工具
    case "weather_report" -> weatherReport(input); // L1: 单工具
    default               -> freeChat(input);    // L0: 纯提示词
};

// L1 技能内部：工具结果是确定性的两步
String weather = MockWeather.query(city);
String time    = LocalDateTime.now().format(...);
String answer  = model.chat(List.of(
        Message.system("你是旅行顾问。基于天气与时间信息输出攻略..."),
        Message.user(input + "\n天气: " + weather + "\n时间: " + time)));
```

::: tip 💡 编排哲学
注意 L1 技能里**没有循环、没有不确定性**：调几次工具、什么顺序，都是人预先设计好的。这叫「编排重于自主」—— 能确定的流程绝不交给模型现场决策，把不确定性留给真正需要它的环节。
:::

## ToyAgent vs ToyAgentScope {#compare}

**Skills：SKILL.md 驱动的渐进式披露** —— 技能是一个目录 + SKILL.md；选哪个技能、按什么步骤做，全由模型读说明书决定，Java 里没有 if/switch。

### L0 / L1 / L2

| 层级 | 内容 | 何时进入上下文 |
| --- | --- | --- |
| L0 元数据 | SKILL.md 头部的 name + description | 常驻系统提示词 |
| L1 说明书 | SKILL.md 正文（操作步骤） | 模型调用 load_skill_through_path 时 |
| L2 资源 | references/*.md 等附件 | 执行到那一步才按需加载 |

```text
src/main/resources/skills/
├── travel_plan/
│   ├── SKILL.md                 # ---
name: travel_plan
description: ...
---
步骤...
│   └── references/checklist.md  # L2 资源
└── weather_report/
    └── SKILL.md
```

```java
ReActAgent.builder()
        .model(model)
        .toolkit(toolkit)                                   // get_weather / get_time
        .skillRepository(new ClasspathSkillRepository("skills"))
        .maxIters(8)
        .build();
```

::: info 🧪 试一试
「帮我做一份杭州旅行攻略」：轨迹里依次出现 加载 SKILL.md → get_weather + get_time → 加载 references/checklist.md → 按说明书格式输出。打成 fat jar 后 Classpath 技能同样可用。
:::

## 对应教程 {#chapters}

- ch08 Skills
- ch12 Skills：工具的组合与复用
