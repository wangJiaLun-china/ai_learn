import DefaultTheme from 'vitepress/theme'
import { h } from 'vue'
import SourceLinks from './SourceLinks.vue'

export default {
  extends: DefaultTheme,
  Layout: () => h(DefaultTheme.Layout, null, {
    'doc-footer-before': () => h(SourceLinks),
  }),
}
