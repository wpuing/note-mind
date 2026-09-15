<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Fold, Expand, ArrowDown } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { clearAdminToken, getAdminToken } from '@/api/ai'
import { fetchMyProfile } from '@/api/http'
import AppSidebar from '@/components/layout/AppSidebar.vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const pageTitle = computed(() => (route.meta.title as string) || '工作台')
const collapsed = computed(() => appStore.sidebarCollapsed)
const displayName = computed(
  () => appStore.currentUser?.nickname || appStore.currentUser?.username || '管理员',
)
const displayRole = computed(() => {
  const r = appStore.currentUser?.role
  if (r === 'ADMIN') return 'Admin'
  if (r === 'USER') return 'User'
  return r || 'Admin'
})
const avatarLetter = computed(() => displayName.value.trim().charAt(0).toUpperCase() || 'A')

function onCommand(cmd: string) {
  if (cmd === 'profile') router.push('/profile')
  if (cmd === 'logout') {
    clearAdminToken()
    appStore.clearCurrentUser()
    router.push('/login')
  }
}

onMounted(async () => {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) return
  if (appStore.currentUser?.id) return
  try {
    const me = await fetchMyProfile()
    appStore.setCurrentUser({
      id: me.id,
      username: me.username,
      nickname: me.nickname || me.username,
      role: me.role || 'USER',
      avatarUrl: me.avatarUrl || '',
    })
  } catch {
    /* ignore */
  }
})
</script>

<template>
  <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
    <AppSidebar />

    <div class="admin-main">
      <header class="admin-header">
        <div class="header-left">
          <button class="icon-btn" type="button" aria-label="折叠侧栏" @click="appStore.toggleSidebar()">
            <el-icon :size="18">
              <Fold v-if="!collapsed" />
              <Expand v-else />
            </el-icon>
          </button>
          <div class="crumb">
            <span class="crumb-muted">管理端</span>
            <span class="crumb-sep">/</span>
            <strong>{{ pageTitle }}</strong>
          </div>
        </div>

        <div class="header-right">
          <el-dropdown trigger="click" @command="onCommand">
            <button class="user-chip" type="button">
              <span class="avatar">{{ avatarLetter }}</span>
              <span class="user-meta">
                <span class="name">{{ displayName }}</span>
                <span class="role">{{ displayRole }}</span>
              </span>
              <el-icon :size="14"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  --current-sidebar: var(--sidebar-width);
  display: grid;
  grid-template-columns: var(--current-sidebar) minmax(0, 1fr);
  height: 100vh;
  overflow: hidden;
  transition: grid-template-columns 0.28s ease;
}

.admin-shell.is-collapsed {
  --current-sidebar: var(--sidebar-collapsed);
}

.admin-main {
  min-width: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin-header {
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 20;
  height: var(--header-height);
  margin: 12px 16px 0;
  padding: 0 16px 0 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #ffffff;
  box-shadow: var(--shadow);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--ink-soft);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.icon-btn:hover {
  background: var(--matcha-100);
  color: var(--matcha-700);
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.crumb-muted { color: var(--ink-muted); }
.crumb-sep { color: var(--matcha-300); }
.crumb strong { font-weight: 600; }

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--line);
  background: #ffffff;
  border-radius: 999px;
  padding: 4px 12px 4px 4px;
  cursor: pointer;
  color: var(--ink);
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(145deg, var(--matcha-400), var(--matcha-700));
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.user-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.15;
}

.user-meta .name { font-size: 13px; font-weight: 600; }
.user-meta .role { font-size: 11px; color: var(--ink-muted); }

/* 路由切换勿用 mode=out-in + 懒加载：离开后进入前会长时间空白甚至卡住 */
.admin-content {
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow: auto;
}

@media (max-width: 900px) {
  .admin-shell,
  .admin-shell.is-collapsed {
    grid-template-columns: 1fr;
  }
  .user-meta { display: none; }
}
</style>
