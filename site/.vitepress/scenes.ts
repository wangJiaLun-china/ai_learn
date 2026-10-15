// 26 个 Agent 场景：slug 即页面文件名，发布后不再改名（外部引用依赖它）
export interface Scene { step: string; slug: string; title: string }
export interface SceneGroup { text: string; items: Scene[] }

export const sceneGroups: SceneGroup[] = [
  { text: '基础内核', items: [
    { step: '01', slug: '01-chat', title: '对话' },
    { step: '02', slug: '02-prompt', title: '提示词' },
    { step: '03', slug: '03-react', title: 'ReAct' },
    { step: '04', slug: '04-tool-call', title: '工具调用' },
  ] },
  { text: '记忆与知识', items: [
    { step: '05', slug: '05-memory', title: '记忆' },
    { step: '06', slug: '06-intent-router', title: '意图路由' },
    { step: '07', slug: '07-rag', title: 'RAG' },
    { step: '08', slug: '08-llm-wiki', title: 'LLM-Wiki' },
  ] },
  { text: '工具的进化', items: [
    { step: '09', slug: '09-mcp', title: 'MCP' },
    { step: '10', slug: '10-skills', title: 'Skills' },
    { step: '11', slug: '11-tool-registry', title: '工具注册表' },
  ] },
  { text: '协作与派遣', items: [
    { step: '12', slug: '12-multi-agent', title: '多智能体' },
    { step: '13', slug: '13-sub-agent', title: '子代理' },
    { step: '14', slug: '14-a2a', title: 'A2A' },
  ] },
  { text: '运行时与工程化', items: [
    { step: '15', slug: '15-loop-runtime', title: 'Loop 运行时' },
    { step: '16', slug: '16-workflow', title: '工作流' },
    { step: '17', slug: '17-react-runtime', title: 'ReAct 运行时' },
    { step: '18', slug: '18-full-pipeline', title: '全流程' },
  ] },
  { text: '人机协同与安全', items: [
    { step: '19', slug: '19-human-in-the-loop', title: '人工介入' },
    { step: '20', slug: '20-approval-gate', title: '审批门禁' },
    { step: '21', slug: '21-sandbox', title: '沙箱' },
    { step: '22', slug: '22-event-sourcing', title: '事件溯源' },
  ] },
  { text: '扩展与形态', items: [
    { step: '23', slug: '23-plugin', title: '插件' },
    { step: '24', slug: '24-hooks', title: 'Hooks' },
    { step: '25', slug: '25-cli', title: 'CLI' },
    { step: '26', slug: '26-scheduler', title: '定时工具' },
  ] },
]
