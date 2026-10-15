# site — AI 学习知识站

线上地址：https://learn.wangjialun.top （VitePress，push 到 main 且改动 `site/` 后由 GitHub Actions 自动发布）

自动发布依赖三个仓库 Secret：`DEPLOY_SSH_KEY`（部署私钥）、`DEPLOY_HOST`（服务器地址）、`DEPLOY_USER`（登录用户）。

```bash
cd site
npm i
npm run docs:dev      # http://localhost:5173
npm run docs:build    # 产物在 .vitepress/dist
```

## 目录

站点只放 AI 相关知识，分四个板块，URL 统一在 `/ai/` 下：

| 路径 | 板块 | 登记位置 |
|------|------|----------|
| `ai/index.md` | 知识地图（总览、学习路线、26 场景演进表） | — |
| `ai/llm/NN-xxx.md` | 一、LLM 基础：Token、采样、Chat API、Function Calling、结构化输出、Embedding | `.vitepress/sections.ts` 的 `llmPages` |
| `ai/agent/NN-xxx.md` | 二、Agent 26 场景笔记：核心概念 / 实现要点 / ToyAgent vs ToyAgentScope / 对应教程 | `.vitepress/scenes.ts` |
| `ai/frameworks/xxx.md` | 三、框架生态：对比总览 + 各框架笔记 | `.vitepress/sections.ts` 的 `frameworkPages` |
| `ai/glossary.md` | 概念速查：单页，每个术语一个固定锚点 | 直接在页内按分组追加 |
| `public/` | 图片等静态资源，引用时写 `/xxx.png` | — |
| `deploy/nginx-learn.conf` | 服务器 nginx 配置留档 | — |

新建一篇：在对应目录加 `xxx.md`，再到「登记位置」加一行，侧边栏自动生成。

## 知识点引用约定

1. **URL 稳定**：文件名就是 URL，发布后不改名。`ai/agent/03-react.md` → `https://learn.wangjialun.top/ai/agent/03-react`。
2. **锚点显式**：需要被引用的小节写固定 id：`## ReAct 循环 {#react-loop}`，不要依赖中文标题自动生成的锚点。
3. **代码里引用**：
   ```java
   // 📖 https://learn.wangjialun.top/ai/agent/03-react#impl
   // 📖 https://learn.wangjialun.top/ai/glossary#mcp
   ```
4. **反向链接**：场景笔记 frontmatter 写 `step: '03'`，页底自动列出 ToyAgent / practice / ToyAgentScope 对应 step 目录的源码链接。
