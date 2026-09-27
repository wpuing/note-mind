<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { clearWebToken, getWebUser } from '@/api/auth'

const router = useRouter()
/** 展示用用户信息（昵称 / 账号 / 角色）。 */
const user = computed(() => getWebUser())

/**
 * 退出登录：清除本地 Web JWT 与用户缓存，跳转登录页。
 */
function logout() {
  clearWebToken()
  void router.push('/login')
}
</script>

<template>
  <!-- 个人中心：只读资料 + 退出 -->
  <section class="profile">
    <h2>个人中心</h2>
    <div class="card">
      <p><span>昵称</span>{{ user?.nickname || '—' }}</p>
      <p><span>账号</span>{{ user?.username || '—' }}</p>
      <p><span>角色</span>{{ user?.role || '—' }}</p>
      <el-button type="danger" plain @click="logout">退出登录</el-button>
    </div>
  </section>
</template>

<style scoped>
/* 居中窄卡片 */
.profile {
  max-width: 640px;
  margin: 32px auto;
  padding: 0 20px;
}
h2 {
  margin: 0 0 16px;
}
.card {
  background: #fff;
  border: 1px solid #e8edf3;
  border-radius: 12px;
  padding: 20px;
  display: grid;
  gap: 12px;
}
p {
  margin: 0;
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 8px;
}
span {
  color: #8a97a6;
}
</style>
