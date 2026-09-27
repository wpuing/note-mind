<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/http'
import { clearAdminToken, getAdminToken, setAdminToken } from '@/api/ai'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

async function goIn(token: string, tip: string, user?: {
  id: string
  username: string
  nickname: string
  role: string
  avatarUrl?: string
}) {
  setAdminToken(token)
  if (user) {
    appStore.setCurrentUser({
      id: user.id,
      username: user.username,
      nickname: user.nickname || user.username,
      role: user.role,
      avatarUrl: user.avatarUrl || '',
    })
  }
  ElMessage.success(tip)
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
  await router.push(redirect || '/dashboard')
}

onMounted(() => {
  // 清掉无效的本地演示 token，避免反复弹「请重新登录」
  const token = getAdminToken() || ''
  if (token.startsWith('local-')) {
    clearAdminToken()
    appStore.clearCurrentUser()
  }
})

async function onSubmit() {
  loading.value = true
  const username = form.username.trim()
  const password = form.password
  try {
    const data = await login(username, password)
    await goIn(data.accessToken, `欢迎，${data.user.nickname || data.user.username}`, data.user)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '登录失败'
    ElMessage.error(
      `${msg}。请确认 Gateway(:8080) 与 Service(:8081) 已启动后再试（文件管理必须使用 Java JWT，不再自动本地放行）。`,
    )
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="mark" />
        <div>
          <h1>NoteMind</h1>
          <p>AI 企业知识库 · 管理端登录</p>
        </div>
      </div>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="admin" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="changeme"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button type="primary" native-type="submit" class="submit" round :loading="loading">
          登录
        </el-button>
      </el-form>
      <p class="tip">
        默认账号 管理员账号（见 `.env.example`）。需 Gateway:8080 + Service:8081 在线；成功后侧栏点「① 文件管理」。
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}

.login-card {
  width: min(420px, 100%);
  padding: 28px;
  border-radius: 24px;
  border: 1px solid var(--line);
  background: #ffffff;
  box-shadow: var(--shadow);
}

.brand {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 22px;
}

.mark {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: linear-gradient(160deg, #c7dec0, #5f914e 60%, #3d5d33);
}

.brand h1 {
  margin: 0;
  font-size: 24px;
  letter-spacing: -0.02em;
}

.brand p {
  margin: 4px 0 0;
  color: var(--ink-muted);
  font-size: 13px;
}

.submit {
  width: 100%;
  margin-top: 8px;
  height: 42px;
  font-weight: 600;
}

.tip {
  margin: 14px 0 0;
  font-size: 12px;
  color: var(--ink-muted);
  line-height: 1.55;
}
</style>
