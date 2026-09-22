<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteAiModels,
  createAiModel,
  deleteAiModel,
  pageAiModels,
  testAiModel,
  updateAiModel,
  type AiModelConfig,
  type AiModelConfigSavePayload,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const loading = ref(false)
const saving = ref(false)
const testingId = ref('')
const rows = ref<AiModelConfig[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
const batchDeleting = ref(false)

const query = reactive({
  name: '',
  modelType: '' as string,
  page: 1,
  pageSize: 8,
})

const COMPAT_BASE = 'https://dashscope.aliyuncs.com/compatible-mode/v1'
const RERANK_NATIVE_BASE =
  'https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank'

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
/** 编辑时展示已保存密钥的脱敏值（不明文回填，避免误把掩码写回库） */
const savedApiKeyMasked = ref('')
const form = reactive({
  name: '',
  modelType: 'EMBEDDING',
  apiStyle: 'openai_compatible',
  provider: '阿里云百炼',
  baseUrl: COMPAT_BASE,
  apiKey: '',
  modelName: '',
  dimension: 1024 as number | null,
  enabled: 1 as number,
  remark: '',
})

const TYPE_OPTIONS = [
  { value: 'EMBEDDING', label: '向量模型', tag: 'primary' as const },
  { value: 'CHAT', label: '对话模型', tag: 'success' as const },
  { value: 'RERANK', label: '重排模型', tag: 'warning' as const },
]

const STYLE_OPTIONS = [
  { value: 'openai_compatible', label: 'OpenAI兼容' },
  { value: 'dashscope_native', label: 'DashScope原生' },
]

/** 市面常见服务商（可允许自定义） */
const PROVIDER_OPTIONS = [
  '阿里云百炼',
  'OpenAI',
  'DeepSeek',
  '智谱 AI',
  '月之暗面 Moonshot',
  'Ollama（本地）',
]

/** 常见接口地址 */
const BASE_URL_OPTIONS = [
  { value: COMPAT_BASE, label: '百炼 OpenAI 兼容' },
  { value: RERANK_NATIVE_BASE, label: '百炼重排原生' },
  { value: 'https://api.openai.com/v1', label: 'OpenAI 官方' },
  { value: 'https://api.deepseek.com/v1', label: 'DeepSeek' },
  { value: 'https://api.moonshot.cn/v1', label: 'Moonshot' },
  { value: 'https://open.bigmodel.cn/api/paas/v4', label: '智谱 AI' },
  { value: 'http://127.0.0.1:11434/v1', label: 'Ollama 本地' },
]

type ModelPreset = {
  modelType: string
  modelName: string
  label: string
  provider: string
  apiStyle: string
  baseUrl: string
  dimension?: number | null
}

/** 市面常见模型目录（按类型筛选；允许自定义输入） */
const MODEL_CATALOG: ModelPreset[] = [
  {
    modelType: 'CHAT',
    modelName: 'qwen-plus',
    label: 'qwen-plus · 通义千问 Plus',
    provider: '阿里云百炼',
    apiStyle: 'openai_compatible',
    baseUrl: COMPAT_BASE,
  },
  {
    modelType: 'CHAT',
    modelName: 'qwen-turbo',
    label: 'qwen-turbo · 通义千问 Turbo',
    provider: '阿里云百炼',
    apiStyle: 'openai_compatible',
    baseUrl: COMPAT_BASE,
  },
  {
    modelType: 'CHAT',
    modelName: 'qwen-max',
    label: 'qwen-max · 通义千问 Max',
    provider: '阿里云百炼',
    apiStyle: 'openai_compatible',
    baseUrl: COMPAT_BASE,
  },
  {
    modelType: 'CHAT',
    modelName: 'qwen-long',
    label: 'qwen-long · 长文本',
    provider: '阿里云百炼',
    apiStyle: 'openai_compatible',
    baseUrl: COMPAT_BASE,
  },
  {
    modelType: 'CHAT',
    modelName: 'deepseek-v3',
    label: 'deepseek-v3 · 百炼托管',
    provider: '阿里云百炼',
    apiStyle: 'openai_compatible',
    baseUrl: COMPAT_BASE,
  },
  {
    modelType: 'CHAT',
    modelName: 'gpt-4o-mini',
    label: 'gpt-4o-mini · OpenAI',
    provider: 'OpenAI',
    apiStyle: 'openai_compatible',
    baseUrl: 'https://api.openai.com/v1',
  },
  {
    modelType: 'CHAT',
    modelName: 'deepseek-chat',
    label: 'deepseek-chat · DeepSeek',
    provider: 'DeepSeek',
    apiStyle: 'openai_compatible',
    baseUrl: 'https://api.deepseek.com/v1',
  },
  {
    modelType: 'EMBEDDING',
    modelName: 'text-embedding-v4',
    label: 'text-embedding-v4 · 百炼（默认 1024 维）',
    provider: '阿里云百炼',
    apiStyle: 'openai_compatible',
    baseUrl: COMPAT_BASE,
    dimension: 1024,
  },
  {
    modelType: 'EMBEDDING',
    modelName: 'text-embedding-v3',
    label: 'text-embedding-v3 · 百炼',
    provider: '阿里云百炼',
    apiStyle: 'openai_compatible',
    baseUrl: COMPAT_BASE,
    dimension: 1024,
  },
  {
    modelType: 'EMBEDDING',
    modelName: 'text-embedding-v2',
    label: 'text-embedding-v2 · 百炼（1536 维）',
    provider: '阿里云百炼',
    apiStyle: 'openai_compatible',
    baseUrl: COMPAT_BASE,
    dimension: 1536,
  },
  {
    modelType: 'EMBEDDING',
    modelName: 'text-embedding-3-small',
    label: 'text-embedding-3-small · OpenAI',
    provider: 'OpenAI',
    apiStyle: 'openai_compatible',
    baseUrl: 'https://api.openai.com/v1',
    dimension: 1536,
  },
  {
    modelType: 'RERANK',
    modelName: 'gte-rerank-v2',
    label: 'gte-rerank-v2 · 百炼重排',
    provider: '阿里云百炼',
    apiStyle: 'dashscope_native',
    baseUrl: RERANK_NATIVE_BASE,
  },
  {
    modelType: 'RERANK',
    modelName: 'gte-rerank',
    label: 'gte-rerank · 百炼重排（旧）',
    provider: '阿里云百炼',
    apiStyle: 'dashscope_native',
    baseUrl: RERANK_NATIVE_BASE,
  },
]

const isEmbedding = computed(() => form.modelType === 'EMBEDDING')
const needBaseUrl = computed(() => form.apiStyle === 'openai_compatible')
const baseUrlPlaceholder = computed(() =>
  form.apiStyle === 'dashscope_native' ? RERANK_NATIVE_BASE : COMPAT_BASE,
)
const apiKeyPlaceholder = computed(() => {
  if (formMode.value === 'edit' && savedApiKeyMasked.value && savedApiKeyMasked.value !== '—') {
    return `已配置 ${savedApiKeyMasked.value}，留空不修改`
  }
  if (formMode.value === 'edit') return '留空则不修改已有密钥'
  return '填写百炼 API Key'
})
const modelNameOptions = computed(() =>
  MODEL_CATALOG.filter((m) => m.modelType === form.modelType),
)

function suggestBaseUrl() {
  if (form.apiStyle === 'dashscope_native') {
    if (!form.baseUrl.trim() || form.baseUrl.trim() === COMPAT_BASE) {
      form.baseUrl = form.modelType === 'RERANK' ? RERANK_NATIVE_BASE : ''
    }
  } else if (!form.baseUrl.trim() || form.baseUrl.trim() === RERANK_NATIVE_BASE) {
    form.baseUrl = COMPAT_BASE
  }
}

function applyModelPreset(modelName: string) {
  const preset = MODEL_CATALOG.find(
    (m) => m.modelType === form.modelType && m.modelName === modelName,
  )
  if (!preset) return
  form.provider = preset.provider
  form.apiStyle = preset.apiStyle
  form.baseUrl = preset.baseUrl
  if (preset.modelType === 'EMBEDDING') {
    form.dimension = preset.dimension ?? 1024
  } else {
    form.dimension = null
  }
  if (formMode.value === 'create' && !form.name.trim()) {
    form.name = `${preset.provider}${typeLabel(preset.modelType)}配置`
  }
}

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

function typeLabel(t?: string) {
  return TYPE_OPTIONS.find((o) => o.value === t)?.label || t || '—'
}

function typeTag(t?: string) {
  return TYPE_OPTIONS.find((o) => o.value === t)?.tag || 'info'
}

function styleLabel(s?: string) {
  return STYLE_OPTIONS.find((o) => o.value === s)?.label || s || '—'
}

function truncateUrl(url?: string) {
  if (!url) return '—'
  return url.length > 42 ? `${url.slice(0, 42)}…` : url
}

async function load() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pageAiModels({
      name: query.name || undefined,
      modelType: query.modelType || undefined,
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

function onSelectionChange(selection: AiModelConfig[]) {
  selectedIds.value = selection.map((r) => r.id).filter(Boolean)
}

function search() {
  query.page = 1
  void load()
}

function resetFilters() {
  query.name = ''
  query.modelType = ''
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
  form.modelType = 'EMBEDDING'
  form.apiStyle = 'openai_compatible'
  form.provider = '阿里云百炼'
  form.baseUrl = COMPAT_BASE
  form.apiKey = ''
  form.modelName = 'text-embedding-v4'
  form.dimension = 1024
  form.enabled = 1
  form.remark = ''
  savedApiKeyMasked.value = ''
}

function openCreate() {
  if (!ensureJavaJwt()) return
  formMode.value = 'create'
  editingId.value = ''
  resetForm()
  formOpen.value = true
}

function openEdit(row: AiModelConfig) {
  if (!ensureJavaJwt()) return
  formMode.value = 'edit'
  editingId.value = row.id
  form.name = row.name
  form.modelType = row.modelType
  form.apiStyle = row.apiStyle || 'openai_compatible'
  form.provider = row.provider || ''
  form.baseUrl = row.baseUrl || ''
  form.apiKey = ''
  form.modelName = row.modelName
  form.dimension = row.dimension ?? null
  form.enabled = row.enabled ?? 1
  form.remark = row.remark || ''
  savedApiKeyMasked.value =
    row.hasApiKey && row.apiKeyMasked && row.apiKeyMasked !== '—' ? row.apiKeyMasked : ''
  suggestBaseUrl()
  formOpen.value = true
}

function onApiStyleChange() {
  suggestBaseUrl()
}

function onModelTypeChange() {
  if (form.modelType !== 'EMBEDDING') form.dimension = null
  else if (form.dimension == null) form.dimension = 1024
  const stillValid = modelNameOptions.value.some((m) => m.modelName === form.modelName)
  if (!stillValid) {
    const first = modelNameOptions.value[0]
    form.modelName = first?.modelName || ''
    if (first) applyModelPreset(first.modelName)
  }
  suggestBaseUrl()
}

function onModelNameChange(name: string) {
  applyModelPreset(name)
}

function buildPayload(): AiModelConfigSavePayload {
  return {
    name: form.name.trim(),
    modelType: form.modelType,
    provider: form.provider.trim() || undefined,
    modelName: form.modelName.trim(),
    baseUrl: form.baseUrl.trim() || undefined,
    apiStyle: form.apiStyle,
    apiKey: form.apiKey.trim() || undefined,
    dimension: form.modelType === 'EMBEDDING' ? form.dimension : null,
    enabled: form.enabled,
    remark: form.remark.trim() || undefined,
  }
}

async function submit() {
  if (!ensureJavaJwt()) return
  if (!form.name.trim()) {
    ElMessage.warning('请填写配置名称')
    return
  }
  if (!form.modelName.trim()) {
    ElMessage.warning('请填写模型名称')
    return
  }
  if (needBaseUrl.value && !form.baseUrl.trim()) {
    ElMessage.warning('OpenAI 兼容协议需填写接口地址')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (formMode.value === 'create') {
      await createAiModel(payload)
      ElMessage.success('已创建')
    } else {
      await updateAiModel(editingId.value, payload)
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

async function removeOne(row: AiModelConfig) {
  if (!ensureJavaJwt()) return
  try {
    await ElMessageBox.confirm(`确认删除模型配置「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteAiModel(row.id)
    ElMessage.success('已删除')
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function removeBatch() {
  if (!ensureJavaJwt()) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选要删除的配置')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedIds.value.length} 条模型配置？`,
      '批量删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const res = await batchDeleteAiModels(selectedIds.value)
    ElMessage.success(`已删除 ${res?.deleted ?? selectedIds.value.length} 条`)
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

async function doTest(row: AiModelConfig) {
  if (!ensureJavaJwt()) return
  testingId.value = row.id
  try {
    const res = await testAiModel(row.id)
    if (res.ok) {
      ElMessage.success(res.message || `测试成功（${res.elapsedMs ?? '—'} ms）`)
    } else {
      ElMessage.error(res.message || '测试失败')
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '测试失败')
  } finally {
    testingId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>模型配置</h2>
        <p>管理对话 / 向量 / 重排模型；密钥脱敏展示，编辑时可更新，测试会实际调用接口。</p>
      </div>
      <el-button round :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="panel-body">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="query.name"
            clearable
            placeholder="请输入配置名称查询"
            style="width: 220px"
            @keyup.enter="search"
          />
          <el-select
            v-model="query.modelType"
            clearable
            placeholder="请选择模型类型"
            style="width: 160px"
          >
            <el-option
              v-for="t in TYPE_OPTIONS"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
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
        empty-text="暂无模型配置"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="配置名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="模型类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" round effect="light" :type="typeTag(row.modelType)">
              {{ typeLabel(row.modelType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接口协议" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ styleLabel(row.apiStyle) }}</template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="接口地址" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip :content="row.baseUrl || '—'" placement="top" :show-after="200">
              <span>{{ truncateUrl(row.baseUrl) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="密钥" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.apiKeyMasked || '—' }}</template>
        </el-table-column>
        <el-table-column label="维度" width="80" align="center">
          <template #default="{ row }">
            {{ row.modelType === 'EMBEDDING' ? (row.dimension ?? '—') : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              round
              effect="light"
              :type="row.enabled ? 'success' : 'info'"
            >
              {{ row.enabled ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                type="success"
                size="small"
                round
                :loading="testingId === row.id"
                @click="doTest(row)"
              >
                测试
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
      title="AI 模型配置"
      variant="form"
      width="640px"
      :close-on-click-modal="!saving"
    >
      <div class="app-modal-body">
        <div class="app-modal-step">
          <div class="form-grid">
            <label><em>*</em>配置名称</label>
            <el-input v-model="form.name" maxlength="128" placeholder="如：百炼向量模型配置" />

            <label><em>*</em>模型类型</label>
            <el-select
              v-model="form.modelType"
              style="width: 100%"
              teleported
              popper-class="app-modal-select-popper"
              @change="onModelTypeChange"
            >
              <el-option
                v-for="t in TYPE_OPTIONS"
                :key="t.value"
                :label="t.label"
                :value="t.value"
              />
            </el-select>

            <label><em>*</em>接口协议</label>
            <el-select
              v-model="form.apiStyle"
              style="width: 100%"
              teleported
              popper-class="app-modal-select-popper"
              @change="onApiStyleChange"
            >
              <el-option
                v-for="s in STYLE_OPTIONS"
                :key="s.value"
                :label="s.label"
                :value="s.value"
              />
            </el-select>

            <label>服务商</label>
            <el-select
              v-model="form.provider"
              filterable
              allow-create
              default-first-option
              teleported
              popper-class="app-modal-select-popper"
              placeholder="选择或输入服务商"
              style="width: 100%"
            >
              <el-option v-for="p in PROVIDER_OPTIONS" :key="p" :label="p" :value="p" />
            </el-select>

            <label><em v-if="needBaseUrl">*</em>接口地址</label>
            <el-select
              v-model="form.baseUrl"
              filterable
              allow-create
              default-first-option
              teleported
              popper-class="app-modal-select-popper"
              :placeholder="baseUrlPlaceholder"
              style="width: 100%"
            >
              <el-option
                v-for="u in BASE_URL_OPTIONS"
                :key="u.value"
                :label="`${u.label} · ${u.value}`"
                :value="u.value"
              />
            </el-select>

            <label>API Key</label>
            <div class="api-key-field">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                maxlength="512"
                :placeholder="apiKeyPlaceholder"
              />
              <p v-if="formMode === 'edit' && savedApiKeyMasked" class="api-key-hint">
                库中已保存：{{ savedApiKeyMasked }}（安全起见不明文回填；仅在需要轮换时输入新密钥）
              </p>
              <p v-else-if="formMode === 'edit'" class="api-key-hint warn">
                尚未保存密钥，请在此填写根目录 .env 中的 DASHSCOPE_*_API_KEY
              </p>
            </div>

            <label><em>*</em>模型名称</label>
            <el-select
              v-model="form.modelName"
              filterable
              allow-create
              default-first-option
              teleported
              popper-class="app-modal-select-popper"
              placeholder="选择市面常见模型，或自定义输入"
              style="width: 100%"
              @change="onModelNameChange"
            >
              <el-option
                v-for="m in modelNameOptions"
                :key="m.modelName"
                :label="m.label"
                :value="m.modelName"
              />
            </el-select>

            <label>向量维度</label>
            <el-input-number
              v-model="form.dimension"
              :min="1"
              :max="8192"
              :disabled="!isEmbedding"
              controls-position="right"
            />

            <label>启用</label>
            <el-radio-group v-model="form.enabled">
              <el-radio :value="1">是</el-radio>
              <el-radio :value="0">否</el-radio>
            </el-radio-group>

            <label>备注</label>
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="3"
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
  align-items: center;
}
.form-grid label {
  font-size: 13px;
  color: var(--ink-soft);
  text-align: right;
}
.form-grid label em {
  color: #c45656;
  font-style: normal;
  margin-right: 2px;
}
.form-grid :deep(.el-textarea),
.form-grid :deep(.el-input-number) {
  width: 100%;
}
.api-key-field {
  display: grid;
  gap: 6px;
}
.api-key-hint {
  margin: 0;
  font-size: 12px;
  color: var(--ink-muted);
  line-height: 1.4;
}
.api-key-hint.warn {
  color: #c45656;
}
</style>
