# ai_learn

王加伦（wangjialun）的个人 AI 学习仓库：记录 AI Agent 相关的学习项目、练习代码和学习进度。

## 目录

| 目录 | 内容 | 技术栈 | 包名 |
|------|------|--------|------|
| [ToyAgent](ToyAgent) | 手写版智能体：26 个渐进式场景，一个场景一个接口类 | Java 17 · 零依赖 · Maven | `com.wangjialun.ai.toyagent` |
| [practice](practice) | 跟着 ToyAgent 逐个场景重写的练习代码 | Java 21 · Gradle | `com.wangjialun.ai.practice` |
| [ToyAgentScope](ToyAgentScope) | 用 AgentScope Java 2.0.4 重做同样的 26 个场景 | Java 17 · Gradle · AgentScope | `com.wangjialun.ai.toyagentscope` |

三个工程各自独立构建，互不引用；各自的 `config.properties` 从同目录的 `config.properties.example` 复制，不入库。

## 学习进度

| 项目 | 状态 | 进度 | 完成时间 |
|------|------|------|----------|
| ToyAgent | ✅ 已学完 | 26 / 26 场景 | 2026-10 |
| practice | ✅ 已完成 | 26 / 26 练习 | 2026-10 |
| ToyAgentScope | 🚧 学习中 | 代码 26 / 26，已掌握 0 / 26 | — |

### ToyAgentScope 场景打卡

学完一个，把对应的 ⬜ 改成 ✅。

| 主题 | 场景 |
|------|------|
| 基础内核 | ⬜ 01 对话 · ⬜ 02 提示词 · ⬜ 03 ReAct · ⬜ 04 工具调用 |
| 记忆与知识 | ⬜ 05 记忆 · ⬜ 06 意图路由 · ⬜ 07 RAG · ⬜ 08 LLM-Wiki |
| 工具的进化 | ⬜ 09 MCP · ⬜ 10 Skills · ⬜ 11 工具注册表 |
| 协作与派遣 | ⬜ 12 多智能体 · ⬜ 13 子代理 · ⬜ 14 A2A |
| 运行时与工程化 | ⬜ 15 Loop 运行时 · ⬜ 16 工作流 · ⬜ 17 ReAct 运行时 · ⬜ 18 全流程 |
| 人机协同与安全 | ⬜ 19 人工介入 · ⬜ 20 审批门禁 · ⬜ 21 沙箱 · ⬜ 22 事件溯源 |
| 扩展与形态 | ⬜ 23 插件 · ⬜ 24 Hooks · ⬜ 25 CLI · ⬜ 26 定时工具 |

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
```
