import { defineConfig } from 'vitepress'
import { sceneGroups } from './scenes'
import { llmPages, frameworkPages } from './sections'

const repo = 'https://github.com/wangJiaLun-china/ai_learn'

export default defineConfig({
  lang: 'zh-CN',
  title: '加伦的学习笔记',
  description: 'AI 与 Agent 学习笔记',
  cleanUrls: true,
  srcExclude: ['README.md', 'deploy/**'],
  lastUpdated: true,
  sitemap: { hostname: 'https://learn.wangjialun.top' },
  themeConfig: {
    nav: [
      { text: 'LLM 基础', link: '/ai/llm/01-token-context', activeMatch: '/ai/llm/' },
      { text: 'Agent 场景', link: '/ai/agent/01-chat', activeMatch: '/ai/agent/' },
      { text: '概念速查', link: '/ai/glossary' },
      { text: '框架生态', link: '/ai/frameworks/', activeMatch: '/ai/frameworks/' },
    ],
    // 按板块分侧边栏，VitePress 优先匹配更深的路径
    sidebar: {
      '/ai/': [
        {
          text: '总览',
          items: [
            { text: '知识地图', link: '/ai/' },
            { text: '概念速查', link: '/ai/glossary' },
            { text: 'LLM 基础', link: `/ai/llm/${llmPages[0].slug}` },
            { text: 'Agent 26 场景', link: `/ai/agent/${sceneGroups[0].items[0].slug}` },
            { text: '框架生态', link: '/ai/frameworks/' },
          ],
        },
      ],
      '/ai/llm/': [
        { text: 'LLM 基础', items: llmPages.map(p => ({ text: p.title, link: `/ai/llm/${p.slug}` })) },
      ],
      '/ai/agent/': sceneGroups.map(g => ({
        text: g.text,
        collapsed: false,
        items: g.items.map(s => ({ text: `${s.step} ${s.title}`, link: `/ai/agent/${s.slug}` })),
      })),
      '/ai/frameworks/': [
        {
          text: '框架生态',
          items: [
            { text: '对比总览', link: '/ai/frameworks/' },
            ...frameworkPages.map(p => ({ text: p.title, link: `/ai/frameworks/${p.slug}` })),
          ],
        },
      ],
    },
    search: {
      provider: 'local',
      options: { translations: { button: { buttonText: '搜索', buttonAriaLabel: '搜索' }, modal: { noResultsText: '没有找到', footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' } } } },
    },
    outline: { level: [2, 3], label: '本页目录' },
    socialLinks: [{ icon: 'github', link: repo }],
    editLink: { pattern: `${repo}/edit/main/site/:path`, text: '在 GitHub 上编辑此页' },
    lastUpdated: { text: '最后更新' },
    docFooter: { prev: '上一篇', next: '下一篇' },
    darkModeSwitchLabel: '外观',
    returnToTopLabel: '回到顶部',
    sidebarMenuLabel: '菜单',
  },
})
