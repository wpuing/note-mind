import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'

const placeholder = () => import('@/views/PlaceholderPage.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginPage.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: AdminLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DashboardPage.vue'), meta: { title: '工作台', group: 'overview' } },
      { path: 'user', name: 'user', component: placeholder, meta: { title: '用户管理', group: 'system' } },
      { path: 'knowledge/base', name: 'knowledge-base', component: () => import('@/views/knowledge/base/KnowledgeBasePage.vue'), meta: { title: '知识库管理', group: 'knowledge' } },
      { path: 'knowledge/chunk-strategy', name: 'chunk-strategy', component: () => import('@/views/knowledge/chunk-strategy/ChunkStrategyPage.vue'), meta: { title: '切分策略', group: 'knowledge' } },
      { path: 'knowledge/retrieval-strategy', name: 'retrieval-strategy', component: () => import('@/views/knowledge/retrieval-strategy/RetrievalStrategyPage.vue'), meta: { title: '检索策略', group: 'knowledge' } },
      {
        path: 'knowledge/document',
        name: 'document',
        component: () => import('@/views/knowledge/document/DocumentPage.vue'),
        meta: { title: '文件管理', group: 'knowledge' },
      },
      // 旧书签兼容：若仍打开「文档管理」占位语义，同样进上传页
      { path: 'knowledge/docs', redirect: '/knowledge/document' },
      {
        path: 'knowledge/segment',
        name: 'segment',
        component: () => import('@/views/knowledge/segment/SegmentPage.vue'),
        meta: { title: '片段管理', group: 'knowledge' },
      },
      { path: 'knowledge/retrieval-test', name: 'retrieval-test', component: () => import('@/views/knowledge/retrieval-test/RetrievalTestPage.vue'), meta: { title: '检索测试', group: 'knowledge' } },
      { path: 'knowledge/recall-bench', name: 'recall-bench', component: () => import('@/views/knowledge/recall-bench/RecallBenchPage.vue'), meta: { title: '召回调试台', group: 'knowledge' } },
      { path: 'ai/model', name: 'ai-model', component: () => import('@/views/ai/model/ModelConfigPage.vue'), meta: { title: '模型配置', group: 'ai' } },
      { path: 'ai/prompt', name: 'ai-prompt', component: () => import('@/views/ai/prompt/PromptTemplatePage.vue'), meta: { title: 'Prompt 模板', group: 'ai' } },
      { path: 'ai/tool', name: 'ai-tool', component: () => import('@/views/ai/tool/AgentToolPage.vue'), meta: { title: '工具中心', group: 'ai' } },
      { path: 'ai/tool-log', name: 'ai-tool-log', component: () => import('@/views/ai/tool/AgentToolCallLogPage.vue'), meta: { title: '工具调用日志', group: 'ai' } },
      { path: 'ai/agent', name: 'ai-agent', component: () => import('@/views/ai/agent/AgentExecutePage.vue'), meta: { title: 'Agent 执行', group: 'ai' } },
      { path: 'ai/agent-run', name: 'ai-agent-run', component: () => import('@/views/ai/agent/AgentRunRecordPage.vue'), meta: { title: 'Agent 运行记录', group: 'ai' } },
      { path: 'app/manage', name: 'qa-app-manage', component: () => import('@/views/app/QaAppManagePage.vue'), meta: { title: '应用管理', group: 'qa' } },
      { path: 'app', name: 'qa-app', component: () => import('@/views/app/QaTestPage.vue'), meta: { title: '问答测试', group: 'qa' } },
      { path: 'chat-log', name: 'chat-log', component: () => import('@/views/app/ChatLogPage.vue'), meta: { title: '对话日志与反馈', group: 'qa' } },
      { path: 'eval/dataset', name: 'eval-dataset', component: () => import('@/views/eval/EvalDatasetPage.vue'), meta: { title: '评测集', group: 'eval' } },
      { path: 'eval/batch', name: 'eval-batch', component: () => import('@/views/eval/EvalBatchPage.vue'), meta: { title: '批量评测', group: 'eval' } },
      { path: 'eval/compare', name: 'eval-compare', component: () => import('@/views/eval/EvalComparePage.vue'), meta: { title: '策略对比', group: 'eval' } },
      { path: 'profile', name: 'profile', component: () => import('@/views/system/ProfilePage.vue'), meta: { title: '个人资料', group: 'system' } },
      { path: 'system/config', name: 'system-config', component: () => import('@/views/system/SystemConfigPage.vue'), meta: { title: '系统配置', group: 'system' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  const token = localStorage.getItem('notemind_admin_token') || ''
  // 演示 local-* 或空 Token 一律回登录（需 Java JWT）
  if ((!token || token.startsWith('local-')) && to.path !== '/login') {
    if (token.startsWith('local-')) localStorage.removeItem('notemind_admin_token')
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || '管理端'
  document.title = `${title} · NoteMind`
})

export default router
