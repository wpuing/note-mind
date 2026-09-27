<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteQaApps,
  createQaApp,
  deleteQaApp,
  listKnowledgeBases,
  listRetrievalStrategies,
  pageQaApps,
  updateQaApp,
  type KnowledgeBase,
  type QaApp,
  type RetrievalStrategy,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const rows = ref<QaApp[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
const batchDeleting = ref(false)
const kbOptions = ref<KnowledgeBase[]>([])
const strategyOptions = ref<RetrievalStrategy[]>([])

const query = reactive({
  name: '',
  enabled: '' as '' | '0' | '1',
  page: 1,
  pageSize: 10,
})

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const form = reactive({
  name: '',
  description: '',
  knowledgeBaseId: '',
  retrievalStrategyId: '',
  enableAgentic: 1 as number,
  historyLimit: 6,
  fallbackReply: '',
  enabled: 1 as number,
})

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

async function loadOptions() {
  try {
    const [kbs, strategies] = await Promise.all([
      listKnowledgeBases(),
      listRetrievalStrategies(),
    ])
    kbOptions.value = kbs || []
    strategyOptions.value = strategies || []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载下拉选项失败')
  }
}

async function load() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pageQaApps({
      name: query.name || undefined,
      enabled: query.enabled === '' ? undefined : Number(query.enabled),
      page: query.page,
      pageSize: query.pageSize,
    })
    rows.value = page?.records || []
    total.value = page?.total || 0
    selectedIds.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function onSelectionChange(selection: QaApp[]) {
  selectedIds.value = selection.map((r) => r.id).filter(Boolean)
}

function search() {
  query.page = 1
  void load()
}

function resetFilters() {
  query.name = ''
  query.enabled = ''
  query.page = 1
  void load()
}

function onPageChange(payload: { page: number; pageSize: number }) {
  query.page = payload.page
  query.pageSize = payload.pageSize
  void load()
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.knowledgeBaseId = kbOptions.value[0]?.id || ''
  const def =
    strategyOptions.value.find((s) => s.isDefault === 1) || strategyOptions.value[0]
  form.retrievalStrategyId = def?.id || ''
  form.enableAgentic = 1
  form.historyLimit = 6
  form.fallbackReply = ''
  form.enabled = 1
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = ''
  resetForm()
  formOpen.value = true
}

function openEdit(row: QaApp) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.knowledgeBaseId = row.knowledgeBaseId
  form.retrievalStrategyId = row.retrievalStrategyId
  form.enableAgentic = row.enableAgentic ?? 1
  form.historyLimit = row.historyLimit ?? 6
  form.fallbackReply = row.fallbackReply || ''
  form.enabled = row.enabled ?? 1
  formOpen.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写应用名称')
    return
  }
  if (!form.knowledgeBaseId) {
    ElMessage.warning('请选择知识库')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      knowledgeBaseId: form.knowledgeBaseId,
      retrievalStrategyId: form.retrievalStrategyId || undefined,
      enableAgentic: form.enableAgentic,
      historyLimit: form.historyLimit,
      fallbackReply: form.fallbackReply.trim() || undefined,
      enabled: form.enabled,
    }
    if (formMode.value === 'create') {
      await createQaApp(payload)
      ElMessage.success('已创建')
    } else {
      await updateQaApp(editingId.value, payload)
      ElMessage.success('已保存')
    }
    formOpen.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: QaApp) {
  try {
    await ElMessageBox.confirm(`确认删除应用「${row.name}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteQaApp(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function removeBatch() {
  if (!ensureJavaJwt()) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选要删除的应用')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedIds.value.length} 个应用？`,
      '批量删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const res = await batchDeleteQaApps(selectedIds.value)
    ElMessage.success(`已删除 ${res?.deleted ?? selectedIds.value.length} 条`)
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

function tryApp(row: QaApp) {
  void router.push({
    path: '/app',
    query: {
      appId: row.id,
      knowledgeBaseId: row.knowledgeBaseId,
    },
  })
}

onMounted(async () => {
  await loadOptions()
  await load()
})
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>应用管理</h2>
        <p>配置问答应用：挂载知识库、检索策略与 Agentic 模式；「试一下」跳转问答测试。</p>
      </div>
    </header>

    <div class="panel-body">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="query.name"
            clearable
            placeholder="应用名称"
            style="width: 200px"
            @keyup.enter="search"
          />
          <el-select v-model="query.enabled" clearable placeholder="状态" style="width: 140px">
            <el-option label="正常" value="1" />
            <el-option label="停用" value="0" />
          </el-select>
          <el-button type="primary" round @click="search">查询</el-button>
          <el-button round @click="resetFilters">重置</el-button>
        </div>
        <div class="toolbar-actions">
          <el-button
            type="danger"
            plain
            round
            :icon="Delete"
            :loading="batchDeleting"
            :disabled="!selectedIds.length"
            @click="removeBatch"
          >
            批量删除
          </el-button>
          <el-button type="primary" round :icon="Plus" @click="openCreate">新增</el-button>
        </div>
      </div>

      <el-table
        :data="rows"
        v-loading="loading"
        stripe
        empty-text="暂无应用"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="应用名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="挂载知识库" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.knowledgeBaseName || row.knowledgeBaseId" size="small" effect="plain">
              {{ row.knowledgeBaseName || row.knowledgeBaseId }}
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="模式" width="120" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              round
              effect="light"
              :type="row.enableAgentic === 1 ? 'warning' : 'info'"
            >
              {{ row.enableAgentic === 1 ? 'Agentic' : '直线链路' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="历史条数" width="100" align="center">
          <template #default="{ row }">{{ row.historyLimit ?? 6 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <span :class="row.enabled === 1 ? 'ok' : 'off'">
              {{ row.enabled === 1 ? '正常' : '停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="success" size="small" round @click="tryApp(row)">试一下</el-button>
            <el-button type="primary" size="small" round :icon="Edit" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" round :icon="Delete" @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <AppPagination
        :total="total"
        :page="query.page"
        :page-size="query.pageSize"
        @change="onPageChange"
      />
    </div>

    <AppModal
      v-model="formOpen"
      :title="formMode === 'create' ? '问答应用' : '问答应用'"
      variant="form"
      width="640px"
    >
      <div class="app-modal-body">
        <div class="app-modal-step form-grid">
          <label class="req">应用名称</label>
          <el-input v-model="form.name" maxlength="64" placeholder="如：售后智能客服" />

          <label>应用说明</label>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="面向谁、回答什么问题"
          />

          <label class="req">挂载知识库</label>
          <el-select
            v-model="form.knowledgeBaseId"
            filterable
            teleported
            popper-class="app-modal-select-popper"
            placeholder="请选择知识库"
            style="width: 100%"
          >
            <el-option
              v-for="kb in kbOptions"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>

          <label>检索策略</label>
          <el-select
            v-model="form.retrievalStrategyId"
            filterable
            teleported
            popper-class="app-modal-select-popper"
            placeholder="默认策略"
            style="width: 100%"
          >
            <el-option
              v-for="s in strategyOptions"
              :key="s.id"
              :label="s.name"
              :value="s.id"
            />
          </el-select>

          <label>Agentic 模式</label>
          <el-radio-group v-model="form.enableAgentic">
            <el-radio :value="0">直线链路</el-radio>
            <el-radio :value="1">Agentic（模型自行判断是否重查）</el-radio>
          </el-radio-group>

          <label>历史消息条数</label>
          <el-input-number v-model="form.historyLimit" :min="0" :max="50" />

          <label>兜底话术</label>
          <el-input
            v-model="form.fallbackReply"
            maxlength="500"
            placeholder="未命中知识库时的回复"
          />

          <label>状态</label>
          <el-radio-group v-model="form.enabled">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </div>
      </div>
      <template #footer>
        <el-button round @click="formOpen = false">取消</el-button>
        <el-button type="primary" round :loading="saving" @click="save">确定</el-button>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.panel {
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--surface);
  box-shadow: var(--shadow);
  min-height: calc(100vh - var(--header-height) - 48px);
}
.panel-head {
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(120deg, rgba(127, 173, 108, 0.14), transparent 55%);
}
.panel-head h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.panel-head p {
  margin: 0;
  color: var(--ink-muted);
  font-size: 13px;
}
.panel-body {
  padding: 16px 20px 24px;
  display: grid;
  gap: 12px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: space-between;
  align-items: center;
}
.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.ok {
  color: #67c23a;
  font-weight: 600;
}
.off {
  color: var(--ink-muted);
}
.form-grid {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 12px 10px;
  align-items: start;
}
.form-grid label {
  text-align: right;
  padding-top: 8px;
  font-size: 13px;
  color: var(--ink-soft);
}
.form-grid label.req::before {
  content: '*';
  color: #f56c6c;
  margin-right: 4px;
}
</style>
