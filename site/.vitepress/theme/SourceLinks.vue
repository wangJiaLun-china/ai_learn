<script setup lang="ts">
// 笔记页 frontmatter 写 `step: '03'`，页底自动列出三个工程里对应场景的源码链接
import { computed } from 'vue'
import { useData } from 'vitepress'

const { frontmatter } = useData()
const base = 'https://github.com/wangJiaLun-china/ai_learn/tree/main'

const links = computed(() => {
  const step = frontmatter.value.step
  if (!step) return []
  return [
    { name: 'ToyAgent', url: `${base}/ToyAgent/src/main/java/com/wangjialun/ai/toyagent/agent/step${step}` },
    { name: 'practice', url: `${base}/practice/src/main/java/com/wangjialun/ai/practice/step${step}` },
    { name: 'ToyAgentScope', url: `${base}/ToyAgentScope/src/main/java/com/wangjialun/ai/toyagentscope/agent/step${step}` },
  ]
})
</script>

<template>
  <div v-if="links.length" class="source-links">
    <p class="title">📂 对应源码</p>
    <ul>
      <li v-for="l in links" :key="l.name"><a :href="l.url" target="_blank" rel="noreferrer">{{ l.name }} · step{{ frontmatter.step }}</a></li>
    </ul>
  </div>
</template>

<style scoped>
.source-links { margin-top: 32px; padding: 12px 16px; border-radius: 8px; background: var(--vp-c-bg-soft); }
.title { margin: 0 0 4px; font-weight: 600; }
ul { margin: 0; padding-left: 20px; }
</style>
