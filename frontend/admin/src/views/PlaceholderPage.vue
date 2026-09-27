<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const title = computed(() => (route.meta.title as string) || '页面')

/** 旧缓存若仍落到占位组件，强制跳到真实上传路由（由 DocumentPage 渲染） */
onMounted(() => {
  if (route.path === '/knowledge/document' || route.name === 'document') {
    router.replace('/knowledge/document')
  }
})

const ready = [
  { path: '/dashboard', label: '工作台' },
  { path: '/knowledge/base', label: '知识库管理' },
  { path: '/knowledge/document', label: '文件管理（功能页）' },
  { path: '/knowledge/segment', label: '片段管理（功能页）' },
  { path: '/knowledge/retrieval-test', label: '检索测试' },
  { path: '/knowledge/recall-bench', label: '召回调试台' },
  { path: '/eval/dataset', label: '评测集管理' },
  { path: '/eval/batch', label: '批量评测' },
  { path: '/eval/compare', label: '策略对比' },
  { path: '/app', label: '问答测试' },
  { path: '/ai/model', label: '模型配置' },
]
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>{{ title }}</h2>
        <p>本页尚未接入业务（占位）。请先用文件管理 / 片段管理 / 检索 / 问答验收。</p>
      </div>
      <span class="badge">待开发</span>
    </header>
    <div class="panel-body">
      <div class="block">
        <h3>当前路由</h3>
        <code>{{ route.fullPath }}</code>
      </div>
      <div class="block">
        <h3>可测页面</h3>
        <ul>
          <li v-for="item in ready" :key="item.path">
            <button type="button" class="link" @click="router.push(item.path)">{{ item.label }}</button>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<style scoped>
.panel {
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--surface);
  box-shadow: var(--shadow);
  overflow: hidden;
  min-height: calc(100vh - var(--header-height) - 48px);
}
.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(120deg, rgba(127, 173, 108, 0.14), transparent 55%);
}
.panel-head h2 { margin: 0 0 6px; font-size: 22px; font-weight: 650; }
.panel-head p { margin: 0; color: var(--ink-muted); font-size: 13px; }
.badge {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f3e8d8;
  color: #8a5a2b;
  font-size: 12px;
  font-weight: 600;
}
.panel-body {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 16px;
  padding: 20px 24px 28px;
}
@media (max-width: 900px) {
  .panel-body { grid-template-columns: 1fr; }
}
.block {
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.55);
}
.block h3 { margin: 0 0 10px; font-size: 14px; }
.block code {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--matcha-50, #eef6ea);
  font-size: 13px;
}
.block ul { margin: 0; padding-left: 0; list-style: none; }
.block li + li { margin-top: 6px; }
.link {
  border: 0;
  background: transparent;
  color: var(--matcha-700, #3d6b2e);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
