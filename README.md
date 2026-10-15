# ai_learn

王加伦（wangjialun）的个人 AI 学习仓库：记录 AI Agent 相关的学习项目、练习代码和学习进度。

## 目录

| 目录 | 内容 | 技术栈 | 包名 |
|------|------|--------|------|
| [ToyAgent](ToyAgent) | 手写版智能体：26 个渐进式场景，一个场景一个接口类 | Java 17 · 零依赖 · Maven | `com.wangjialun.ai.toyagent` |
| [practice](practice) | 跟着 ToyAgent 逐个场景重写的练习代码 | Java 21 · Gradle | `com.wangjialun.ai.practice` |
| [ToyAgentScope](ToyAgentScope) | 用 AgentScope Java 2.0.4 重做同样的 26 个场景 | Java 17 · Gradle · AgentScope | `com.wangjialun.ai.toyagentscope` |
| [site](site) | 学习知识站 [learn.wangjialun.top](https://learn.wangjialun.top)：前端 + AI 笔记 | VitePress · Node | — |

三个 Java 工程各自独立构建，互不引用；各自的 `config.properties` 从同目录的 `config.properties.example` 复制，不入库。

## 学习进度

| 项目 | 状态 | 进度 | 完成时间 |
|------|------|------|----------|
| ToyAgent | ✅ 已学完 | 26 / 26 场景 | 2026-10 |
| practice | ✅ 已完成 | 26 / 26 练习 | 2026-10 |
| ToyAgentScope | 🚧 学习中 | 代码 26 / 26，已掌握 0 / 26 | — |

### ToyAgentScope 场景打卡

学完一个，把对应的 ⬜ 改成 ✅。场景名链接到知识站对应笔记。

| 主题 | 场景 |
|------|------|
| 基础内核 | ⬜ [01 对话](https://learn.wangjialun.top/ai/agent/01-chat) · ⬜ [02 提示词](https://learn.wangjialun.top/ai/agent/02-prompt) · ⬜ [03 ReAct](https://learn.wangjialun.top/ai/agent/03-react) · ⬜ [04 工具调用](https://learn.wangjialun.top/ai/agent/04-tool-call) |
| 记忆与知识 | ⬜ [05 记忆](https://learn.wangjialun.top/ai/agent/05-memory) · ⬜ [06 意图路由](https://learn.wangjialun.top/ai/agent/06-intent-router) · ⬜ [07 RAG](https://learn.wangjialun.top/ai/agent/07-rag) · ⬜ [08 LLM-Wiki](https://learn.wangjialun.top/ai/agent/08-llm-wiki) |
| 工具的进化 | ⬜ [09 MCP](https://learn.wangjialun.top/ai/agent/09-mcp) · ⬜ [10 Skills](https://learn.wangjialun.top/ai/agent/10-skills) · ⬜ [11 工具注册表](https://learn.wangjialun.top/ai/agent/11-tool-registry) |
| 协作与派遣 | ⬜ [12 多智能体](https://learn.wangjialun.top/ai/agent/12-multi-agent) · ⬜ [13 子代理](https://learn.wangjialun.top/ai/agent/13-sub-agent) · ⬜ [14 A2A](https://learn.wangjialun.top/ai/agent/14-a2a) |
| 运行时与工程化 | ⬜ [15 Loop 运行时](https://learn.wangjialun.top/ai/agent/15-loop-runtime) · ⬜ [16 工作流](https://learn.wangjialun.top/ai/agent/16-workflow) · ⬜ [17 ReAct 运行时](https://learn.wangjialun.top/ai/agent/17-react-runtime) · ⬜ [18 全流程](https://learn.wangjialun.top/ai/agent/18-full-pipeline) |
| 人机协同与安全 | ⬜ [19 人工介入](https://learn.wangjialun.top/ai/agent/19-human-in-the-loop) · ⬜ [20 审批门禁](https://learn.wangjialun.top/ai/agent/20-approval-gate) · ⬜ [21 沙箱](https://learn.wangjialun.top/ai/agent/21-sandbox) · ⬜ [22 事件溯源](https://learn.wangjialun.top/ai/agent/22-event-sourcing) |
| 扩展与形态 | ⬜ [23 插件](https://learn.wangjialun.top/ai/agent/23-plugin) · ⬜ [24 Hooks](https://learn.wangjialun.top/ai/agent/24-hooks) · ⬜ [25 CLI](https://learn.wangjialun.top/ai/agent/25-cli) · ⬜ [26 定时工具](https://learn.wangjialun.top/ai/agent/26-scheduler) |

## 下一步学习计划

- [ ] 学完 ToyAgentScope 的 26 个场景，对照 ToyAgent 写对比笔记
- [ ] （待补充）

## 启动方式

```bash
# ToyAgent（零依赖，javac 直接编译）
cd ToyAgent
javac -encoding UTF-8 -d target/classes $(find src/main/java -name "*.java")
java -cp target/classes com.wangjialun.ai.toyagent.Application   # http://localhost:8099

# practice
cd practice
./gradlew -q run --args='step05'   # 不带参数则跑全部场景

# ToyAgentScope
cd ToyAgentScope
./gradlew run                      # http://localhost:8099

# site（知识站）
cd site
npm i && npm run docs:dev          # http://localhost:5173
```
