<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/http'
import { clearWebToken, getWebToken } from '@/api/auth'

const router = useRouter()
const route = useRoute()
/** 提交中：禁用按钮防重复点击。 */
const loading = ref(false)
/** 登录表单：账号 / 密码。 */
const form = reactive({
  username: '',
  password: '',
})

// 进入页时：已有合法 JWT 则直达首页；旧 local-* 演示 token 一律清掉
const existing = getWebToken() || ''
// 真实 JWT（非 local- 前缀）：已登录，跳过登录表单
if (existing && !existing.startsWith('local-')) {
  void router.replace('/')
} else if (existing.startsWith('local-')) {
  // 历史演示放行 token，文档接口不可用，强制清除
  clearWebToken()
}

/**
 * 提交登录：调 Gateway 鉴权，成功后按 redirect 或默认「/」跳转。
 */
async function onSubmit() {
  loading.value = true
  try {
    // 调登录 API 并落盘 token + user
    const data = await login(form.username.trim(), form.password)
    ElMessage.success(`欢迎，${data.user.nickname || data.user.username}`)
    // 支持 ?redirect= 回跳；非法类型则回首页
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect || '/')
  } catch (e) {
    // 展示业务/网络错误文案
    ElMessage.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    // 无论成败都恢复按钮
    loading.value = false
  }
}
</script>

<template>
  <!-- 居中登录卡片 -->
  <div class="login">
    <form class="card" @submit.prevent="onSubmit">
      <h1>NoteMind</h1>
      <p>高级企业知识库 · 用户端</p>
      <label>账号</label>
      <el-input v-model="form.username" placeholder="demo" autocomplete="username" />
      <label>密码</label>
      <el-input
        v-model="form.password"
        type="password"
        show-password
        placeholder="changeme"
        autocomplete="current-password"
      />
      <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%; margin-top: 12px">
        登录
      </el-button>
      <p class="tip">默认账号 演示账号（见 `.env.example`）（需 Gateway + Service）</p>
    </form>
  </div>
</template>

<style scoped>
/* 全屏居中 + 轻量氛围渐变 */
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at 20% 20%, rgba(79, 140, 255, 0.18), transparent 40%),
    radial-gradient(circle at 80% 0%, rgba(120, 200, 160, 0.16), transparent 35%),
    #f4f7fb;
}
.card {
  width: min(400px, 92vw);
  background: #fff;
  border: 1px solid #e6ebf2;
  border-radius: 16px;
  padding: 28px 24px;
  box-shadow: 0 12px 40px rgba(31, 42, 55, 0.08);
  display: grid;
  gap: 8px;
}
h1 {
  margin: 0;
  font-size: 28px;
}
p {
  margin: 0 0 8px;
  color: #6b7785;
  font-size: 13px;
}
label {
  font-size: 13px;
  color: #5b6b7c;
  margin-top: 6px;
}
.tip {
  margin-top: 10px;
  font-size: 12px;
}
</style>
