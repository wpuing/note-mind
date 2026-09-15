<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, View } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteAgentTools,
  createAgentTool,
  deleteAgentTool,
  fetchAgentToolOpenaiDefinitions,
  fetchAgentToolStats,
  pageAgentTools,
  updateAgentTool,
  updateAgentToolEnabled,
  type AgentTool,
  type AgentToolSavePayload,
  type AgentToolStat,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const togglingId = ref('')
const rows = ref<AgentTool[]>([])
const total = ref(0)
const selectedRows = ref<AgentTool[]>([])
const batchDeleting = ref(false)

const query = reactive({
  name: '',
  enabled: '' as '' | '0' | '1',
  page: 1,
  pageSize: 8,
})

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const form = reactive({
  code: '',
  name: '',
  description: '',
  schemaJson: '{\n  "query": "检索问题"\n}',
  sortNo: 1,
  enabled: 1 as number,
  implemented: 1 as number,
})

const defOpen = ref(false)
const defLoading = ref(false)
const defJson = ref('')
const defScopeLabel = ref('')
const defItems = ref<{ code: string; name: string }[]>([])

const statsOpen = ref(false)
const statsLoading = ref(false)
const statsRows = ref<AgentToolStat[]>([])

const defButtonLabel = computed(() =>
  selectedRows.value.length > 0
    ? `查看所选工具定义（${selectedRows.value.length}）`
    : '查看模型收到的工具定义',
)

const statsButtonLabel = computed(() =>
  selectedRows.value.length > 0
    ? `调用统计（已选 ${selectedRows.value.length}）`
    : '调用统计',
)

const statsScopeHint = computed(() =>
  selectedRows.value.length > 0
    ? `当前仅统计已选 ${selectedRows.value.length} 个工具；点击次数或「日志」可跳转调用日志。`
    : '未勾选时统计全部工具；点击次数或「日志」可跳转到调用日志并按该工具筛选。',
)

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

function prettyJson(raw?: string) {
  if (!raw) return '{}'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function parseParamDescs(schemaJson?: string): Record<string, string> {
  if (!schemaJson) return {}
  try {
    const node = JSON.parse(schemaJson)
    if (!node || typeof node !== 'object' || Array.isArray(node)) return {}
    if (node.properties && typeof node.properties === 'object') {
      const out: Record<string, string> = {}
      for (const [k, v] of Object.entries(node.properties as Record<string, any>)) {
        out[k] = typeof v?.description === 'string' ? v.description : ''
      }
      return out
    }
    const out: Record<string, string> = {}
    for (const [k, v] of Object.entries(node)) {
      out[k] = typeof v === 'string' ? v : JSON.stringify(v)
    }
    return out
  } catch {
    return {}
  }
}

function toOpenAiTool(tool: AgentTool) {
  const properties: Record<string, { type: string; description: string }> = {}
  const required: string[] = []
  for (const [k, desc] of Object.entries(parseParamDescs(tool.schemaJson))) {
    properties[k] = { type: 'string', description: desc || '' }
    required.push(k)
  }
  return {
    type: 'function',
    function: {
      name: tool.code,
      description: tool.description || '',
      parameters: {
        type: 'object',
        properties,
        required,
      },
    },
  }
}

function showDefinitions(tools: AgentTool[], scopeLabel: string) {
  defItems.value = tools.map((t) => ({ code: t.code, name: t.name }))
  defScopeLabel.value = scopeLabel
  defJson.value = JSON.stringify(
    tools.map((t) => toOpenAiTool(t)),
    null,
    2,
  )
  defOpen.value = true
  defLoading.value = false
}

function onSelectionChange(selection: AgentTool[]) {
  selectedRows.value = selection
}

async function load() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pageAgentTools({
      name: query.name || undefined,
      enabled: query.enabled === '' ? undefined : Number(query.enabled),
      page: query.page,
      pageSize: query.pageSize,
    })
    rows.value = page?.records || []
    total.value = page?.total || 0
    selectedRows.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
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
  form.code = ''
  form.name = ''
  form.description = ''
  form.schemaJson = '{\n  "query": "检索问题",\n  "kb_id": "知识库ID",\n  "top_k": "召回条数"\n}'
  form.sortNo = (total.value || 0) + 1
  form.enabled = 1
  form.implemented = 1
}

function openCreate() {
  if (!ensureJavaJwt()) return
  formMode.value = 'create'
  editingId.value = ''
  resetForm()
  formOpen.value = true
}

function openEdit(row: AgentTool) {
  if (!ensureJavaJwt()) return
  formMode.value = 'edit'
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.description = row.description || ''
  form.schemaJson = prettyJson(row.schemaJson)
  form.sortNo = row.sortNo ?? 0
  form.enabled = row.enabled ?? 1
  form.implemented = row.implemented ?? 1
  formOpen.value = true
}

function buildPayload(): AgentToolSavePayload {
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    description: form.description.trim(),
    schemaJson: form.schemaJson.trim() || '{}',
    sortNo: form.sortNo,
    enabled: form.enabled,
    implemented: form.implemented,
  }
}

async function submit() {
  if (!ensureJavaJwt()) return
  if (!form.code.trim() || !form.name.trim() || !form.description.trim()) {
    ElMessage.warning('请填写工具编码、名称与描述')
    return
  }
  try {
    JSON.parse(form.schemaJson || '{}')
  } catch {
    ElMessage.warning('入参说明必须是合法 JSON 对象')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (formMode.value === 'create') {
      await createAgentTool(payload)
      ElMessage.success('已创建')
    } else {
      await updateAgentTool(editingId.value, payload)
      ElMessage.success('已保存')
    }
    formOpen.value = false
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function onToggleEnabled(row: AgentTool, val: boolean | string | number) {
  if (!ensureJavaJwt()) return
  const enabled = val === true || val === 1 || val === '1' ? 1 : 0
  togglingId.value = row.id
  try {
    await updateAgentToolEnabled(row.id, enabled)
    row.enabled = enabled
  } catch (e) {
    row.enabled = enabled ? 0 : 1
    ElMessage.error(e instanceof Error ? e.message : '更新启用状态失败')
  } finally {
    togglingId.value = ''
  }
}

async function removeOne(row: AgentTool) {
  if (!ensureJavaJwt()) return
  try {
    await ElMessageBox.confirm(`确认删除工具「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAgentTool(row.id)
    ElMessage.success('已删除')
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function removeBatch() {
  if (!ensureJavaJwt()) return
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选要删除的工具')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedRows.value.length} 个工具？`,
      '批量删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const ids = selectedRows.value.map((r) => r.id).filter(Boolean)
    const res = await batchDeleteAgentTools(ids)
    ElMessage.success(`已删除 ${res?.deleted ?? ids.length} 条`)
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

async function openDefinitions() {
  if (!ensureJavaJwt()) return
  defOpen.value = true
  defLoading.value = true
  defJson.value = ''
  defItems.value = []
  defScopeLabel.value = ''
  try {
    if (selectedRows.value.length > 0) {
      const tools = [...selectedRows.value]
      const label =
        tools.length === 1
          ? `当前选中：${tools[0].code}（${tools[0].name}）`
          : `当前选中 ${tools.length} 条：${tools.map((t) => t.code).join('、')}`
      showDefinitions(tools, label)
      return
    }
    const data = await fetchAgentToolOpenaiDefinitions()
    defJson.value = data?.json || '[]'
    try {
      const arr = JSON.parse(defJson.value) as Array<{ function?: { name?: string } }>
      defItems.value = (arr || []).map((item) => {
        const code = item?.function?.name || '—'
        const local = rows.value.find((r) => r.code === code)
        return { code, name: local?.name || code }
      })
    } catch {
      defItems.value = []
    }
    defScopeLabel.value =
      defItems.value.length > 0
        ? `全部已启用工具（${defItems.value.length}）：${defItems.value.map((t) => `${t.code}（${t.name}）`).join('、')}`
        : '当前没有已启用的工具'
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载工具定义失败')
    defOpen.value = false
  } finally {
    defLoading.value = false
  }
}

function openDefinitionOne(row: AgentTool) {
  if (!ensureJavaJwt()) return
  showDefinitions([row], `当前工具：${row.code}（${row.name}）`)
}

async function openStats() {
  if (!ensureJavaJwt()) return
  statsOpen.value = true
  statsLoading.value = true
  try {
    const codes = selectedRows.value.map((t) => t.code)
    statsRows.value = (await fetchAgentToolStats(codes.length ? codes : undefined)) || []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载统计失败')
    statsOpen.value = false
  } finally {
    statsLoading.value = false
  }
}

function goToolLogs(row: AgentToolStat, status?: 'OK' | 'FAILED') {
  statsOpen.value = false
  const query: Record<string, string> = { toolCode: row.toolCode }
  if (status) query.status = status
  void router.push({ path: '/ai/tool-log', query })
}

onMounted(() => void load())
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>工具中心</h2>
        <p>
          登记 Agent 可用工具。当前执行链路已接入
          <code>search_knowledge</code>（知识库检索）；未实现的工具仅作定义预览，不会出现在调用日志中。
          勾选行可查看 OpenAI tools[] 定义（仅导出已启用且已实现）。
        </p>
      </div>
      <el-button round :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="panel-body">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="query.name"
            clearable
            placeholder="请输入工具名称查询"
            style="width: 220px"
            @keyup.enter="search"
          />
          <el-select v-model="query.enabled" clearable placeholder="启用状态" style="width: 140px">
            <el-option label="启用" value="1" />
            <el-option label="停用" value="0" />
          </el-select>
          <el-button type="primary" round @click="search">查询</el-button>
          <el-button round @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div class="action-row">
        <el-button type="primary" round :icon="Plus" @click="openCreate">新增</el-button>
        <el-button
          type="danger"
          plain
          round
          :icon="Delete"
          :loading="batchDeleting"
          :disabled="!selectedRows.length"
          @click="removeBatch"
        >
          批量删除
        </el-button>
        <el-button type="success" round plain @click="openDefinitions">{{ defButtonLabel }}</el-button>
        <el-button round @click="openStats">{{ statsButtonLabel }}</el-button>
        <span v-if="selectedRows.length" class="selection-hint">
          已选 {{ selectedRows.length }} 条：定义 / 统计 / 批量删除针对所选工具
        </span>
      </div>

      <el-table
        :data="rows"
        v-loading="loading"
        stripe
        empty-text="暂无工具"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="code" label="工具编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="工具名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="工具描述（会发给模型）" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column label="代码实现" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              round
              effect="light"
              :type="row.implemented ? 'success' : 'info'"
            >
              {{ row.implemented ? '已实现' : '未实现' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              :loading="togglingId === row.id"
              @change="(v: boolean | string | number) => onToggleEnabled(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序" width="80" align="center" />
        <el-table-column label="操作" width="240" align="center">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button type="success" size="small" round :icon="View" @click="openDefinitionOne(row)">
                定义
              </el-button>
              <el-button type="primary" size="small" round :icon="Edit" @click="openEdit(row)">
                编辑
              </el-button>
              <el-button type="danger" size="small" round :icon="Delete" @click="removeOne(row)">
                删除
              </el-button>
            </div>
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
      title="Function 工具"
      variant="form"
      width="680px"
      :close-on-click-modal="!saving"
    >
      <div class="app-modal-body">
        <div class="app-modal-step">
          <div class="form-grid">
            <label><em>*</em>工具编码</label>
            <el-input
              v-model="form.code"
              maxlength="64"
              placeholder="如：search_knowledge"
              :disabled="formMode === 'edit'"
            />

            <label><em>*</em>工具名称</label>
            <el-input v-model="form.name" maxlength="128" placeholder="如：知识库检索" />

            <label><em>*</em>工具描述</label>
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              maxlength="512"
              placeholder="会发给模型的功能说明"
            />

            <label>入参说明</label>
            <el-input
              v-model="form.schemaJson"
              type="textarea"
              :rows="6"
              placeholder='{"query":"检索问题","kb_id":"知识库ID"}'
            />

            <label>排序</label>
            <el-input-number v-model="form.sortNo" :min="0" :max="9999" controls-position="right" />

            <label>启用</label>
            <el-radio-group v-model="form.enabled">
              <el-radio :value="1">是</el-radio>
              <el-radio :value="0">否</el-radio>
            </el-radio-group>

            <label>代码实现</label>
            <el-radio-group v-model="form.implemented">
              <el-radio :value="1">已实现</el-radio>
              <el-radio :value="0">未实现</el-radio>
            </el-radio-group>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button round :disabled="saving" @click="formOpen = false">取消</el-button>
        <el-button type="primary" round :loading="saving" @click="submit">确定</el-button>
      </template>
    </AppModal>

    <AppModal v-model="defOpen" title="模型收到的工具定义" variant="form" width="720px">
      <div class="app-modal-body">
        <div class="app-modal-step" v-loading="defLoading">
          <p class="def-scope">{{ defScopeLabel }}</p>
          <div v-if="defItems.length" class="def-tags">
            <el-tag
              v-for="t in defItems"
              :key="t.code"
              size="small"
              round
              effect="plain"
              type="success"
            >
              {{ t.code }}
              <template v-if="t.name"> · {{ t.name }}</template>
            </el-tag>
          </div>
          <pre class="json-block">{{ defJson || '[]' }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button round @click="defOpen = false">关闭</el-button>
      </template>
    </AppModal>

    <AppModal v-model="statsOpen" title="调用统计" variant="form" width="800px">
      <div class="app-modal-body">
        <div class="app-modal-step" v-loading="statsLoading">
          <p class="stats-hint">{{ statsScopeHint }}</p>
          <el-table :data="statsRows" stripe empty-text="暂无调用记录">
            <el-table-column prop="toolCode" label="工具编码" min-width="130" />
            <el-table-column prop="toolName" label="工具名称" min-width="110" />
            <el-table-column label="调用次数" width="100" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="goToolLogs(row)">{{ row.callCount }}</el-button>
              </template>
            </el-table-column>
            <el-table-column label="成功" width="80" align="center">
              <template #default="{ row }">
                <el-button link type="success" @click="goToolLogs(row, 'OK')">{{ row.successCount }}</el-button>
              </template>
            </el-table-column>
            <el-table-column label="失败" width="80" align="center">
              <template #default="{ row }">
                <el-button link type="danger" @click="goToolLogs(row, 'FAILED')">{{ row.failCount }}</el-button>
              </template>
            </el-table-column>
            <el-table-column label="平均耗时(ms)" width="110" align="center">
              <template #default="{ row }">
                {{ row.avgLatencyMs == null ? '—' : Math.round(row.avgLatencyMs) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center">
              <template #default="{ row }">
                <el-button type="primary" size="small" round @click="goToolLogs(row)">日志</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button round @click="router.push('/ai/tool-log'); statsOpen = false">全部日志</el-button>
        <el-button round @click="statsOpen = false">关闭</el-button>
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
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
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
  max-width: 640px;
}
.panel-body {
  padding: 16px 20px 24px;
  display: grid;
  gap: 12px;
}
.toolbar,
.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.row-actions {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
}
.row-actions :deep(.el-button) {
  margin: 0;
}
.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.form-grid {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 14px 12px;
  align-items: start;
}
.form-grid label {
  font-size: 13px;
  color: var(--ink-soft);
  text-align: right;
  padding-top: 8px;
}
.form-grid label em {
  color: #c45656;
  font-style: normal;
  margin-right: 2px;
}
.form-grid :deep(.el-input-number) {
  width: 160px;
}
.json-block {
  margin: 0;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f3f5f2;
  border: 1px solid var(--line);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.55;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  max-height: min(60vh, 520px);
  overflow: auto;
}
.def-scope {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--ink);
  font-weight: 600;
}
.def-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}
.selection-hint {
  font-size: 12px;
  color: var(--ink-muted);
}
.stats-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--ink-muted);
}
</style>
