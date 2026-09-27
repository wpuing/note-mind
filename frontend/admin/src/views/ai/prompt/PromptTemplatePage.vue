<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, View } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeletePromptTemplates,
  createPromptTemplate,
  deletePromptTemplate,
  pagePromptTemplates,
  updatePromptTemplate,
  type PromptTemplate,
  type PromptTemplateSavePayload,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const SCENARIO_OPTIONS = ['问答生成', '查询改写', 'Agentic RAG', '评测集构建', '效果评测']

const VAR_PATTERN = /\{\{([a-zA-Z_][a-zA-Z0-9_]*)\}\}|\{([a-zA-Z_][a-zA-Z0-9_]*)\}/g

const loading = ref(false)
const saving = ref(false)
const rows = ref<PromptTemplate[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
const batchDeleting = ref(false)

const query = reactive({
  name: '',
  scenario: '' as string,
  page: 1,
  pageSize: 8,
})

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const form = reactive({
  code: '',
  name: '',
  scenario: '问答生成',
  content: '',
  enabled: 1 as number,
  remark: '',
})

const previewOpen = ref(false)
const previewRow = ref<PromptTemplate | null>(null)
const previewVars = ref<string[]>([])
const previewValues = reactive<Record<string, string>>({})
const previewResult = ref('')

const contentVarsHint = computed(() => {
  const vars = extractVariables(form.content)
  return vars.length ? vars.join(', ') : '（从模板内容自动识别 {var} / {{var}}）'
})

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

function extractVariables(content?: string): string[] {
  if (!content) return []
  const seen = new Set<string>()
  const out: string[] = []
  const re = new RegExp(VAR_PATTERN.source, 'g')
  let m: RegExpExecArray | null
  while ((m = re.exec(content))) {
    const name = m[1] || m[2]
    if (name && !seen.has(name)) {
      seen.add(name)
      out.push(name)
    }
  }
  return out
}

function renderTemplate(content: string, values: Record<string, string>) {
  const re = /\{\{([a-zA-Z_][a-zA-Z0-9_]*)\}\}|\{([a-zA-Z_][a-zA-Z0-9_]*)\}/g
  return content.replace(re, (_full, a: string, b: string) => {
    const key = a || b
    const v = values[key]
    return v == null || v === '' ? `{${key}}` : v
  })
}

function truncate(text?: string, n = 48) {
  if (!text) return '—'
  const one = text.replace(/\s+/g, ' ').trim()
  return one.length > n ? `${one.slice(0, n)}…` : one
}

async function load() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pagePromptTemplates({
      name: query.name || undefined,
      scenario: query.scenario || undefined,
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

function onSelectionChange(selection: PromptTemplate[]) {
  selectedIds.value = selection.map((r) => r.id).filter(Boolean)
}

function search() {
  query.page = 1
  void load()
}

function resetFilters() {
  query.name = ''
  query.scenario = ''
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
  form.scenario = '问答生成'
  form.content =
    '你是企业知识库助手。请严格根据下面提供的资料回答用户问题。\n\n资料：\n{context}\n\n用户问题：{question}'
  form.enabled = 1
  form.remark = ''
}

function openCreate() {
  if (!ensureJavaJwt()) return
  formMode.value = 'create'
  editingId.value = ''
  resetForm()
  formOpen.value = true
}

function openEdit(row: PromptTemplate) {
  if (!ensureJavaJwt()) return
  formMode.value = 'edit'
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.scenario = row.scenario || '问答生成'
  form.content = row.content || ''
  form.enabled = row.enabled ?? 1
  form.remark = row.remark || ''
  formOpen.value = true
}

function buildPayload(): PromptTemplateSavePayload {
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    scenario: form.scenario || undefined,
    content: form.content,
    enabled: form.enabled,
    remark: form.remark.trim() || undefined,
  }
}

async function submit() {
  if (!ensureJavaJwt()) return
  if (!form.code.trim()) {
    ElMessage.warning('请填写模板编码')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请填写模板名称')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写模板内容')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (formMode.value === 'create') {
      await createPromptTemplate(payload)
      ElMessage.success('已创建')
    } else {
      await updatePromptTemplate(editingId.value, payload)
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

async function removeOne(row: PromptTemplate) {
  if (!ensureJavaJwt()) return
  try {
    await ElMessageBox.confirm(`确认删除 Prompt「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deletePromptTemplate(row.id)
    ElMessage.success('已删除')
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function removeBatch() {
  if (!ensureJavaJwt()) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选要删除的模板')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedIds.value.length} 条 Prompt 模板？`,
      '批量删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const res = await batchDeletePromptTemplates(selectedIds.value)
    ElMessage.success(`已删除 ${res?.deleted ?? selectedIds.value.length} 条`)
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

function openPreview(row: PromptTemplate) {
  previewRow.value = row
  const vars =
    row.variables
      ?.split(',')
      .map((s) => s.trim())
      .filter(Boolean) || extractVariables(row.content)
  const unique = vars.length ? vars : extractVariables(row.content)
  previewVars.value = unique
  Object.keys(previewValues).forEach((k) => delete previewValues[k])
  for (const v of unique) {
    previewValues[v] = v === 'count' ? '1' : ''
  }
  previewResult.value = ''
  previewOpen.value = true
}

function generatePreview() {
  const content = previewRow.value?.content || ''
  previewResult.value = renderTemplate(content, { ...previewValues })
}

onMounted(() => void load())
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>Prompt 模板</h2>
        <p>
          管理问答 / 改写 / Agent / 评测等提示词；内容支持
          <code>{var}</code> 占位，预览时可回填变量。
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
            placeholder="请输入模板名称查询"
            style="width: 220px"
            @keyup.enter="search"
          />
          <el-select
            v-model="query.scenario"
            clearable
            placeholder="请选择使用场景"
            style="width: 180px"
          >
            <el-option v-for="s in SCENARIO_OPTIONS" :key="s" :label="s" :value="s" />
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
        empty-text="暂无 Prompt 模板"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="code" label="模板编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="模板名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="scenario" label="使用场景" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.scenario || '—' }}</template>
        </el-table-column>
        <el-table-column label="变量" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.variables || '—' }}</template>
        </el-table-column>
        <el-table-column label="模板内容" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ truncate(row.content, 56) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" round effect="light" :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button type="success" size="small" round :icon="View" @click="openPreview(row)">
                预览
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
      title="Prompt 模板"
      variant="form"
      width="720px"
      :close-on-click-modal="!saving"
    >
      <div class="app-modal-body">
        <div class="app-modal-step">
          <div class="form-grid">
            <label><em>*</em>模板编码</label>
            <el-input
              v-model="form.code"
              maxlength="64"
              placeholder="如：RAG_ANSWER"
              :disabled="formMode === 'edit'"
            />

            <label><em>*</em>模板名称</label>
            <el-input v-model="form.name" maxlength="128" placeholder="如：知识库问答" />

            <label>使用场景</label>
            <el-select
              v-model="form.scenario"
              teleported
              popper-class="app-modal-select-popper"
              placeholder="请选择使用场景"
              style="width: 100%"
            >
              <el-option v-for="s in SCENARIO_OPTIONS" :key="s" :label="s" :value="s" />
            </el-select>

            <label><em>*</em>模板内容</label>
            <div class="content-field">
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="10"
                maxlength="20000"
                placeholder="支持 {context}、{question} 等占位符"
              />
              <p class="field-hint">识别到的变量：{{ contentVarsHint }}</p>
            </div>

            <label>启用</label>
            <el-radio-group v-model="form.enabled">
              <el-radio :value="1">是</el-radio>
              <el-radio :value="0">否</el-radio>
            </el-radio-group>

            <label>备注</label>
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="2"
              maxlength="512"
              placeholder="用途说明"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button round :disabled="saving" @click="formOpen = false">取消</el-button>
        <el-button type="primary" round :loading="saving" @click="submit">确定</el-button>
      </template>
    </AppModal>

    <AppModal v-model="previewOpen" title="预览 Prompt" variant="form" width="640px">
      <div class="app-modal-body">
        <div class="app-modal-step">
          <p v-if="!previewVars.length" class="field-hint">该模板没有可识别的占位变量。</p>
          <div v-else class="preview-vars">
            <div v-for="v in previewVars" :key="v" class="preview-var-row">
              <label>{{ v }}</label>
              <el-input v-model="previewValues[v]" :placeholder="`填写 ${v}`" />
            </div>
          </div>
          <el-button type="primary" round class="preview-gen" @click="generatePreview">
            生成预览
          </el-button>
          <pre v-if="previewResult" class="preview-result">{{ previewResult }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button round @click="previewOpen = false">关闭</el-button>
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
.panel-head code {
  font-size: 12px;
  padding: 0 4px;
  border-radius: 4px;
  background: rgba(75, 116, 61, 0.1);
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
  align-items: center;
  justify-content: space-between;
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
.row-actions {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
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
.content-field {
  display: grid;
  gap: 6px;
}
.field-hint {
  margin: 0;
  font-size: 12px;
  color: var(--ink-muted);
  line-height: 1.4;
}
.preview-vars {
  display: grid;
  gap: 12px;
  margin-bottom: 12px;
}
.preview-var-row {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}
.preview-var-row label {
  text-align: right;
  font-size: 13px;
  color: var(--ink-soft);
}
.preview-gen {
  margin-bottom: 12px;
}
.preview-result {
  margin: 0;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f3f5f2;
  border: 1px solid var(--line);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.65;
  color: var(--ink);
  font-family: inherit;
}
</style>
