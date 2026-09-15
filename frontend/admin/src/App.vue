<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { RouterView, useRouter, useRoute } from 'vue-router'
import { clearAdminToken } from '@/api/ai'

const router = useRouter()
const route = useRoute()
const bootError = ref<string | null>(null)

function isAuthError(msg: string) {
  return /未登录|Token\s*无效|登录已失效|请重新登录/.test(msg)
}

onErrorCaptured((err) => {
  const msg = err instanceof Error ? err.message : String(err)
  console.error('[NoteMind Admin]', err)

  // 鉴权失败：清 Token 并回登录页，不要整页「渲染失败」
  if (isAuthError(msg)) {
    clearAdminToken()
    bootError.value = null
    if (route.path !== '/login') {
      void router.replace({ path: '/login', query: { redirect: route.fullPath } })
    }
    return false
  }

  bootError.value = msg
  return false
})
</script>

<template>
  <div v-if="bootError" class="boot-error">
    <h1>页面渲染失败</h1>
    <pre>{{ bootError }}</pre>
    <p>请打开控制台查看完整堆栈，或强制刷新（Ctrl+Shift+R）后重新登录。</p>
    <p>
      <a href="/login">前往登录</a>
    </p>
  </div>
  <RouterView v-else />
</template>

<style scoped>
.boot-error {
  margin: 48px auto;
  max-width: 640px;
  padding: 24px;
  border: 1px solid #e8b4b4;
  border-radius: 12px;
  background: #fff5f5;
  color: #7a1f1f;
  font-family: ui-sans-serif, system-ui, sans-serif;
}
.boot-error h1 { margin: 0 0 12px; font-size: 18px; }
.boot-error pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  background: #fff;
  padding: 12px;
  border-radius: 8px;
}
.boot-error a { color: #1d4ed8; }
</style>
