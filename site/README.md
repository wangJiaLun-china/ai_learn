# site — 学习知识站

线上地址：https://learn.wangjialun.top （VitePress，push 到 main 且改动 `site/` 后由 GitHub Actions 自动发布）

```bash
cd site
npm i
npm run docs:dev      # http://localhost:5173
npm run docs:build    # 产物在 .vitepress/dist
```

## 目录

| 路径 | 内容 |
|------|------|
| `frontend/` | 前端笔记 |
| `ai/agent/NN-xxx.md` | 26 个 Agent 场景笔记，场景清单在 `.vitepress/scenes.ts` |
| `public/` | 图片等静态资源，引用时写 `/xxx.png` |
| `deploy/nginx-learn.conf` | 服务器 nginx 配置留档 |

## 知识点引用约定

1. **URL 稳定**：文件名就是 URL，发布后不改名。`ai/agent/03-react.md` → `https://learn.wangjialun.top/ai/agent/03-react`。
2. **锚点显式**：需要被引用的小节写固定 id：`## ReAct 循环 {#react-loop}`，不要依赖中文标题自动生成的锚点。
3. **代码里引用**：
   ```java
   // 📖 https://learn.wangjialun.top/ai/agent/03-react#react-loop
   ```
4. **反向链接**：场景笔记 frontmatter 写 `step: '03'`，页底自动列出 ToyAgent / practice / ToyAgentScope 对应 step 目录的源码链接。
