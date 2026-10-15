// LLM 基础 / 框架生态两个板块的页面清单：slug 即页面文件名，发布后不再改名（外部引用依赖它）
export interface Page { slug: string; title: string }

export const llmPages: Page[] = [
  { slug: '01-token-context', title: 'Token 与上下文窗口' },
  { slug: '02-sampling', title: '采样参数' },
  { slug: '03-chat-api', title: 'Chat API 与流式输出' },
  { slug: '04-function-calling', title: 'Function Calling' },
  { slug: '05-structured-output', title: '结构化输出' },
  { slug: '06-embedding', title: 'Embedding 与向量检索' },
]

export const frameworkPages: Page[] = [
  { slug: 'agentscope', title: 'AgentScope Java' },
  { slug: 'langgraph', title: 'LangChain / LangGraph' },
  { slug: 'spring-ai', title: 'Spring AI' },
  { slug: 'claude-agent-sdk', title: 'Claude Agent SDK' },
]
