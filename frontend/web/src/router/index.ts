/**
 * 用户端路由：登录页公开；首页与个人中心需有效 JWT。
 */
import { createRouter, createWebHistory } from 'vue-router'
import { clearWebToken, getWebToken } from '@/api/auth'
import WebLayout from '@/layouts/WebLayout.vue'

/** 创建 history 模式路由表 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      // 懒加载登录页，减少首屏体积
      component: () => import('@/views/auth/LoginPage.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: WebLayout,
      children: [
        {
          path: '',
          name: 'home',
          // 懒加载问答首页
          component: () => import('@/views/home/HomeChatPage.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'profile',
          name: 'profile',
          // 懒加载个人中心
          component: () => import('@/views/profile/ProfilePage.vue'),
          meta: { title: '个人中心' },
        },
      ],
    },
  ],
})

/**
 * 全局前置守卫：非公开路由必须持有非 local- 前缀的 JWT。
 */
router.beforeEach((to) => {
  // 公开页（如登录）直接放行
  if (to.meta.public) return true
  // 读取本地 token
  const token = getWebToken() || ''
  // 无 token 或仍是旧的本地演示 token → 强制去登录
  if (!token || token.startsWith('local-')) {
    // 清理无效的 local- 演示 token，避免反复误判
    if (token.startsWith('local-')) clearWebToken()
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

/**
 * 路由切换后更新浏览器标题。
 */
router.afterEach((to) => {
  document.title = `${(to.meta.title as string) || 'NoteMind'} · 高级企业知识库`
})

export default router
