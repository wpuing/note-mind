<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminToken, clearAdminToken } from '@/api/ai'
import {
  changeMyPassword,
  fetchMyProfile,
  updateMyProfile,
  type UserProfile,
} from '@/api/http'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const appStore = useAppStore()

const loading = ref(false)
const savingProfile = ref(false)
const savingPwd = ref(false)
const profile = ref<UserProfile | null>(null)

const profileForm = reactive({
  nickname: '',
  avatarUrl: '',
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const roleLabel = computed(() => {
  const r = profile.value?.role
  if (r === 'ADMIN') return '管理员'
  if (r === 'USER') return '普通用户'
  return r || '—'
})

const displayName = computed(() => {
  const n = (profileForm.nickname || profile.value?.nickname || '').trim()
  // 避免乱码/问号昵称盖过用户名
  if (!n || /^[?？]+$/.test(n)) {
    return profile.value?.username || '用户'
  }
  return n
})

const avatarLetter = computed(() => {
  const n = displayName.value
  const ch = n.trim().charAt(0)
  return /[a-z]/i.test(ch) ? ch.toUpperCase() : ch || 'U'
})

function ensureJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录')
    void router.push('/login')
    return false
  }
  return true
}

async function load() {
  if (!ensureJwt()) return
  loading.value = true
  try {
    const me = await fetchMyProfile()
    profile.value = me
    const nick = (me.nickname || '').trim()
    profileForm.nickname = !nick || /^[?？]+$/.test(nick) ? me.username || '' : nick
    profileForm.avatarUrl = me.avatarUrl || ''
    appStore.setCurrentUser({
      id: me.id,
      username: me.username,
      nickname: profileForm.nickname,
      role: me.role || 'USER',
      avatarUrl: me.avatarUrl || '',
    })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载资料失败')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!ensureJwt()) return
  const nickname = profileForm.nickname.trim()
  if (!nickname) {
    ElMessage.warning('请填写昵称')
    return
  }
  savingProfile.value = true
  try {
    const me = await updateMyProfile({
      nickname,
      avatarUrl: profileForm.avatarUrl.trim(),
    })
    profile.value = me
    appStore.setCurrentUser({
      id: me.id,
      username: me.username,
      nickname: me.nickname || me.username,
      role: me.role || 'USER',
      avatarUrl: me.avatarUrl || '',
    })
    ElMessage.success('资料已保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  if (!ensureJwt()) return
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原密码与新密码')
    return
  }
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  try {
    await ElMessageBox.confirm('修改密码后需使用新密码重新登录，是否继续？', '修改密码', {
      type: 'warning',
      confirmButtonText: '确认修改',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  savingPwd.value = true
  try {
    await changeMyPassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success('密码已修改，请重新登录')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    clearAdminToken()
    appStore.clearCurrentUser()
    await router.push('/login')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '修改密码失败')
  } finally {
    savingPwd.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="panel" v-loading="loading">
    <header class="panel-head">
      <div>
        <h2>个人资料</h2>
        <p>查看与编辑当前登录账号信息，支持修改登录密码。</p>
      </div>
      <el-button round :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="panel-body" v-if="profile">
      <aside class="identity">
        <div class="id-top">
          <div class="avatar-lg">{{ avatarLetter }}</div>
          <div class="id-text">
            <h3>{{ displayName }}</h3>
            <p>@{{ profile.username }}</p>
            <el-tag size="small" round effect="light" type="success">{{ roleLabel }}</el-tag>
          </div>
        </div>
        <ul class="meta">
          <li>
            <span>用户 ID</span>
            <strong>{{ profile.id }}</strong>
          </li>
          <li>
            <span>状态</span>
            <strong :class="profile.status === 0 ? 'bad' : 'ok'">
              {{ profile.status === 0 ? '停用' : '正常' }}
            </strong>
          </li>
          <li>
            <span>创建时间</span>
            <strong>{{ profile.createTime || '—' }}</strong>
          </li>
          <li>
            <span>更新时间</span>
            <strong>{{ profile.updateTime || '—' }}</strong>
          </li>
        </ul>
      </aside>

      <div class="forms">
        <article class="card">
          <h3>基本信息</h3>
          <p class="hint">用户名不可修改；昵称会显示在顶栏。</p>
          <div class="form-grid">
            <label>用户名</label>
            <el-input :model-value="profile.username" disabled />
            <label>昵称</label>
            <el-input v-model="profileForm.nickname" maxlength="64" show-word-limit placeholder="显示名称" />
            <label>头像地址</label>
            <el-input
              v-model="profileForm.avatarUrl"
              maxlength="512"
              placeholder="可选，图片 URL（当前顶栏仍用首字母头像）"
            />
          </div>
          <div class="actions">
            <el-button type="primary" round :loading="savingProfile" @click="saveProfile">保存资料</el-button>
          </div>
        </article>

        <article class="card">
          <h3>修改密码</h3>
          <p class="hint">新密码 6～64 位；修改成功后需重新登录。</p>
          <div class="form-grid">
            <label>原密码</label>
            <el-input
              v-model="pwdForm.oldPassword"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="当前登录密码"
            />
            <label>新密码</label>
            <el-input
              v-model="pwdForm.newPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="至少 6 位"
            />
            <label>确认新密码</label>
            <el-input
              v-model="pwdForm.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="再次输入新密码"
            />
          </div>
          <div class="actions">
            <el-button type="danger" plain round :loading="savingPwd" @click="savePassword">
              修改密码
            </el-button>
          </div>
        </article>
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
.panel-head h2 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 650;
}
.panel-head p {
  margin: 0;
  color: var(--ink-muted);
  font-size: 13px;
}
.panel-body {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  padding: 20px 24px 28px;
}
.identity {
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 16px;
  background: linear-gradient(180deg, var(--matcha-50), #fff 56%);
  position: sticky;
  top: 16px;
}
.id-top {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}
.avatar-lg {
  flex: 0 0 auto;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(145deg, var(--matcha-400), var(--matcha-700));
  color: #fff;
  font-size: 22px;
  font-weight: 700;
}
.id-text {
  min-width: 0;
  text-align: left;
}
.id-text h3 {
  margin: 0 0 2px;
  font-size: 17px;
  font-weight: 650;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.id-text p {
  margin: 0 0 6px;
  color: var(--ink-muted);
  font-size: 12px;
}
.meta {
  margin: 0;
  padding: 4px 0 0;
  list-style: none;
  display: grid;
  gap: 0;
}
.meta li {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  align-items: baseline;
  padding: 10px 2px;
  border-bottom: 1px dashed rgba(75, 116, 61, 0.16);
  font-size: 13px;
}
.meta li:last-child {
  border-bottom: 0;
  padding-bottom: 2px;
}
.meta span {
  color: var(--ink-muted);
  font-size: 12px;
}
.meta strong {
  font-weight: 600;
  color: var(--ink);
  word-break: break-all;
  line-height: 1.4;
}
.meta strong.ok {
  color: var(--matcha-700);
}
.meta strong.bad {
  color: #b42318;
}
.forms {
  display: grid;
  gap: 14px;
}
.card {
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 18px 20px 16px;
  background: #fff;
}
.card h3 {
  margin: 0 0 4px;
  font-size: 16px;
}
.hint {
  margin: 0 0 14px;
  font-size: 12px;
  color: var(--ink-muted);
}
.form-grid {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 12px 14px;
  align-items: center;
}
.form-grid label {
  font-size: 13px;
  color: var(--ink-soft);
}
.actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
@media (max-width: 900px) {
  .panel-body {
    grid-template-columns: 1fr;
  }
  .identity {
    position: static;
  }
  .form-grid {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}
</style>
