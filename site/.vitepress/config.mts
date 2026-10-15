import { defineConfig } from 'vitepress'
import { sceneGroups } from './scenes'

const repo = 'https://github.com/wangJiaLun-china/ai_learn'

export default defineConfig({
  lang: 'zh-CN',
  title: '加伦的学习笔记',
  description: '前端与 AI Agent 学习笔记',
  cleanUrls: true,
  srcExclude: ['README.md', 'deploy/**'],
  lastUpdated: true,
  sitemap: { hostname: 'https://learn.wangjialun.top' },
  themeConfig: {
    nav: [
      { text: '前端', link: '/frontend/' },
      { text: 'AI', link: '/ai/' },
    ],
    sidebar: {
      '/frontend/': [
        { text: '前端', items: [{ text: '概览', link: '/frontend/' }] },
      ],
      '/ai/': [
        { text: 'AI', items: [{ text: '概览', link: '/ai/' }] },
        ...sceneGroups.map(g => ({
          text: g.text,
          collapsed: false,
          items: g.items.map(s => ({ text: `${s.step} ${s.title}`, link: `/ai/agent/${s.slug}` })),
        })),
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
