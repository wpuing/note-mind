<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshRight } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  listSystemConfigs,
  reloadSystemConfigs,
  saveSystemConfigs,
  type SystemConfigItem,
} from '@/api/http'

const GROUP_LABEL: Record<string, string> = {
  knowledge: '知识库 / 分页',
  upload: '上传',
  prompt: 'Prompt',
  retrieval: '检索',
  llm: '大模型',
  chat: '会话',
  general: '其它',
}

const loading = ref(false)
const saving = ref(false)
const rows = ref<SystemConfigItem[]>([])
const draft = reactive<Record<string, string>>({})

const groups = computed(() => {
  const map = new Map<string, SystemConfigItem[]>()
  for (const row of rows.value) {
    const g = row.group || 'general'
    if (!map.has(g)) map.set(g, [])
    map.get(g)!.push(row)
  }
  return [...map.entries()].map(([key, items]) => ({
    key,
    title: GROUP_LABEL[key] || key,
    items,
  }))
})

const dirty = computed(() =>
  rows.value.some((r) => (draft[r.key] ?? '') !== (r.value ?? '')),
)

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

function applyRows(list: SystemConfigItem[]) {
  rows.value = list
  for (const k of Object.keys(draft)) delete draft[k]
  for (const row of list) {
    draft[row.key] = row.value ?? ''
  }
}

async function load() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const list = await listSystemConfigs()
    applyRows(list || [])
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!ensureJavaJwt() || !dirty.value) return
  saving.value = true
  try {
    const items = rows.value
      .filter((r) => r.editable !== 0)
      .map((r) => ({ key: r.key, value: draft[r.key] ?? '' }))
    const list = await saveSystemConfigs(items)
    applyRows(list || [])
    ElMessage.success('已保存，业务侧即时生效（内存缓存已更新）')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function reloadCache() {
  if (!ensureJavaJwt()) return
  try {
    await reloadSystemConfigs()
    await load()
    ElMessage.success('已从数据库重载缓存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '重载失败')
  }
}

function resetDraft() {
  for (const row of rows.value) {
    draft[row.key] = row.value ?? ''
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="page-head">
      <div>
        <h1>系统配置</h1>
        <p class="desc">
          高频默认值可在此修改；保存后 Java 业务即时生效。未配置项回落
          <code>application.yml / .env</code>。
        </p>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button :icon="RefreshRight" @click="reloadCache">重载缓存</el-button>
        <el-button @click="resetDraft" :disabled="!dirty">还原未保存</el-button>
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">
          保存修改
        </el-button>
      </div>
    </header>

    <section v-for="g in groups" :key="g.key" class="group-card">
      <h2>{{ g.title }}</h2>
      <div class="cfg-list">
        <div v-for="item in g.items" :key="item.key" class="cfg-row">
          <div class="meta">
            <div class="label">{{ item.label || item.key }}</div>
            <div class="key">{{ item.key }} · {{ item.valueType }}</div>
            <div v-if="item.description" class="hint">{{ item.description }}</div>
          </div>
          <div class="editor">
            <el-input
              v-model="draft[item.key]"
              :disabled="item.editable === 0"
              :placeholder="item.valueType === 'csv' ? '逗号分隔' : '配置值'"
              clearable
            />
          </div>
        </div>
      </div>
    </section>

    <el-empty v-if="!loading && !rows.length" description="暂无配置，请确认 Service 已启动并连上 MySQL" />
  </div>
</template>

<style scoped>
.page {
  padding: 4px 2px 24px;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.page-head h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: #1f2a37;
}
.desc {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.5;
  max-width: 640px;
}
.desc code {
  font-size: 12px;
  background: #f3f4f6;
  padding: 1px 6px;
  border-radius: 4px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.group-card {
  background: #fff;
  border: 1px solid #e8ecf1;
  border-radius: 10px;
  padding: 14px 16px 6px;
  margin-bottom: 14px;
}
.group-card h2 {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 650;
  color: #1f4e79;
}
.cfg-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.1fr) minmax(260px, 1fr);
  gap: 12px 20px;
  padding: 12px 0;
  border-top: 1px solid #f0f2f5;
}
.meta .label {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}
.meta .key {
  margin-top: 2px;
  font-size: 12px;
  color: #9ca3af;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.meta .hint {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}
@media (max-width: 900px) {
  .page-head {
    flex-direction: column;
  }
  .cfg-row {
    grid-template-columns: 1fr;
  }
}
</style>
