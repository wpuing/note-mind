<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { clearWebToken, getWebUser } from '@/api/auth'

const route = useRoute()
const router = useRouter()
/** 顶栏展示用：从本地缓存读取当前 Web 用户。 */
const user = computed(() => getWebUser())

/**
 * 路由跳转封装；void 忽略 Promise，避免未处理 rejection 告警。
 */
function go(path: string) {
  void router.push(path)
}

/**
 * 用户下拉菜单命令：个人中心 / 退出登录。
 */
function onCommand(cmd: string) {
  // 进入个人中心页
  if (cmd === 'profile') void router.push('/profile')
  // 清 JWT 并回到登录页
  if (cmd === 'logout') {
    clearWebToken()
    void router.push('/login')
  }
}
</script>

<template>
  <!-- 用户端壳：顶栏 + 可滚动主内容区 -->
  <div class="web-shell">
    <!-- 顶栏：品牌 / 导航 / 用户菜单 -->
    <header class="topbar">
      <div class="brand" @click="go('/')">高级企业知识库平台</div>
      <!-- 主导航：首页 / 个人中心，active 跟随当前 path -->
      <nav class="nav">
        <button type="button" :class="{ active: route.path === '/' }" @click="go('/')">首页</button>
        <button type="button" :class="{ active: route.path === '/profile' }" @click="go('/profile')">
          个人中心
        </button>
      </nav>
      <!-- 右上角用户下拉 -->
      <el-dropdown trigger="click" @command="onCommand">
        <button class="user" type="button">
          <span class="avatar">{{ (user?.nickname || '用').slice(0, 1) }}</span>
          <span class="name">{{ user?.nickname || user?.username || '用户' }}</span>
          <el-icon :size="14"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人中心</el-dropdown-item>
            <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>
    <!-- 子路由出口：聊天页 / 个人中心等 -->
    <main class="body">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
/* 全屏壳：列布局，仅主区滚动 */
.web-shell {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f5f8fc 0%, #eef2f7 100%);
  color: #1f2a37;
}
/* 顶栏三栏网格：品牌 | 导航 | 用户 */
.topbar {
  height: 56px;
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e8edf3;
  position: sticky;
  top: 0;
  z-index: 20;
}
.brand {
  font-weight: 700;
  font-size: 16px;
  cursor: pointer;
  justify-self: start;
}
.nav {
  display: flex;
  gap: 8px;
  justify-self: center;
}
.nav button {
  border: 0;
  background: transparent;
  padding: 8px 14px;
  cursor: pointer;
  color: #5b6b7c;
  font-size: 14px;
  border-bottom: 2px solid transparent;
}
.nav button.active {
  color: #2f6bff;
  border-bottom-color: #2f6bff;
  font-weight: 600;
}
.user {
  justify-self: end;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  cursor: pointer;
  color: inherit;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #7eb6ff, #4d8dff);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 700;
}
.name {
  font-size: 14px;
}
.body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
