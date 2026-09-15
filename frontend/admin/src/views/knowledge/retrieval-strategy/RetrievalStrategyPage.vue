<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteRetrievalStrategies,
  createRetrievalStrategy,
  deleteRetrievalStrategy,
  pageRetrievalStrategies,
  updateRetrievalStrategy,
  type RetrievalStrategy,
  type RetrievalStrategySavePayload,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const rows = ref<RetrievalStrategy[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
const batchDeleting = ref(false)

const query = reactive({ name: '', page: 1, pageSize: 8 })

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const form = reactive({
  name: '',
  enableVector: 1,
  enableBm25: 0,
  enableRrf: 0,
  enableRerank: 0,
  enableRewrite: 0,
  enableParentFill: 0,
  topK: 5,
  rerankTopN: 4,
  vectorTopK: 5,
  bm25TopK: 0,
  rrfK: 60,
  cosineThreshold: 0.3 as number | null,
  rerankThreshold: null as number | null,
  rewriteMode: 'multi_query',
  rewriteCount: 3,
  enabled: 1,
  isDefault: 0,
  remark: '',
})

const REWRITE_OPTIONS = [
  { value: 'multi_query', label: '多查询扩展' },
  { value: 'coref', label: '指代消解' },
  { value: 'hyde', label: 'HyDE 假想文档' },
  { value: 'all', label: '全部' },
]

const showRrf = computed(() => form.enableVector === 1 && form.enableBm25 === 1)
const scoreMetric = computed(() => {
  if (form.enableRerank === 1) return 'rerank'
  if (showRrf.value) return 'rrf'
  if (form.enableVector === 1) return 'cosine'
  return 'bm25'
})
const scoreMetricLabel = computed(() => {
  switch (scoreMetric.value) {
    case 'rerank':
      return '重排相关分'
    case 'rrf':
      return 'RRF名次分'
    case 'cosine':
      return '余弦相似度'
    default:
      return 'BM25'
  }
})

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

function flag(v?: number) {
  return v === 1
}

function rewriteModeLabel(mode?: string) {
  switch ((mode || '').toLowerCase()) {
    case 'multi_query':
      return '多查询扩展'
    case 'coref':
      return '指代消解'
    case 'hyde':
      return 'HyDE'
    case 'all':
      return '全部改写'
    default:
      return mode || '改写'
  }
}

type CapTag = { key: string; text: string; tone: string }

function capabilityTags(row: RetrievalStrategy): CapTag[] {
  const tags: CapTag[] = []
  if (flag(row.enableVector)) {
    tags.push({ key: 'v', text: `向量 ${row.vectorTopK ?? row.topK ?? '—'}`, tone: 'cap-vector' })
  }
  if (flag(row.enableBm25)) {
    tags.push({ key: 'b', text: `BM25 ${row.bm25TopK ?? '—'}`, tone: 'cap-bm25' })
  }
  if (flag(row.enableRerank)) {
    tags.push({ key: 'r', text: `重排 ${row.rerankTopN ?? row.topK ?? '—'}`, tone: 'cap-rerank' })
  }
  if (flag(row.enableRewrite)) {
    tags.push({ key: 'w', text: rewriteModeLabel(row.rewriteMode), tone: 'cap-rewrite' })
  }
  if (flag(row.enableParentFill)) {
    tags.push({ key: 'p', text: '父块回填', tone: 'cap-parent' })
  }
  return tags
}

function formatThreshold(v?: number | null) {
  if (v == null || Number.isNaN(Number(v))) return '—'
  const n = Number(v)
  return Number.isInteger(n) ? String(n) : String(n)
}

function rrfDisplay(row: RetrievalStrategy) {
  if (flag(row.enableVector) && flag(row.enableBm25)) return row.rrfK ?? 60
  return '—'
}

async function load() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pageRetrievalStrategies({
      name: query.name.trim() || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    rows.value = page.records || []
    total.value = page.total ?? 0
    selectedIds.value = []
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
  search()
}

function onPageChange(payload: { page: number; pageSize: number }) {
  query.page = payload.page
  query.pageSize = payload.pageSize
  void load()
}

function onSelectionChange(selection: RetrievalStrategy[]) {
  selectedIds.value = selection.map((r) => r.id).filter(Boolean)
}

function resetForm() {
  form.name = ''
  form.enableVector = 1
  form.enableBm25 = 0
  form.enableRrf = 0
  form.enableRerank = 0
  form.enableRewrite = 0
  form.enableParentFill = 0
  form.topK = 5
  form.rerankTopN = 4
  form.vectorTopK = 5
  form.bm25TopK = 0
  form.rrfK = 60
  form.cosineThreshold = 0.3
  form.rerankThreshold = null
  form.rewriteMode = 'multi_query'
  form.rewriteCount = 3
  form.enabled = 1
  form.isDefault = 0
  form.remark = ''
}

function openCreate() {
  if (!ensureJavaJwt()) return
  formMode.value = 'create'
  editingId.value = ''
  resetForm()
  formOpen.value = true
}

function openEdit(row: RetrievalStrategy) {
  if (!ensureJavaJwt()) return
  formMode.value = 'edit'
  editingId.value = row.id
  form.name = row.name
  form.enableVector = row.enableVector ?? 1
  form.enableBm25 = row.enableBm25 ?? 0
  form.enableRrf = row.enableRrf ?? 0
  form.enableRerank = row.enableRerank ?? 0
  form.enableRewrite = row.enableRewrite ?? 0
  form.enableParentFill = row.enableParentFill ?? 0
  form.topK = row.topK ?? 5
  form.rerankTopN = row.rerankTopN ?? 4
  form.vectorTopK = row.vectorTopK ?? 5
  form.bm25TopK = row.bm25TopK ?? 0
  form.rrfK = row.rrfK ?? 60
  form.cosineThreshold = row.cosineThreshold ?? null
  form.rerankThreshold = row.rerankThreshold ?? null
  form.rewriteMode = row.rewriteMode || 'multi_query'
  form.rewriteCount = row.rewriteCount ?? 3
  form.enabled = row.enabled ?? 1
  form.isDefault = row.isDefault ?? 0
  form.remark = row.remark || ''
  formOpen.value = true
}

function goRetrievalTest(row: RetrievalStrategy) {
  router.push({
    path: '/knowledge/retrieval-test',
    query: { retrievalStrategyId: row.id },
  })
}

function buildPayload(): RetrievalStrategySavePayload {
  const enableRrf =
    form.enableVector === 1 && form.enableBm25 === 1 ? (form.enableRrf === 0 ? 0 : 1) : 0
  return {
    name: form.name.trim(),
    enableVector: form.enableVector,
    enableBm25: form.enableBm25,
    enableRrf,
    enableRerank: form.enableRerank,
    enableRewrite: form.enableRewrite,
    enableParentFill: form.enableParentFill,
    topK: form.enableRerank === 1 ? form.rerankTopN : form.topK,
    rerankTopN: form.rerankTopN,
    vectorTopK: form.enableVector === 1 ? form.vectorTopK : 0,
    bm25TopK: form.enableBm25 === 1 ? form.bm25TopK : 0,
    rrfK: form.rrfK,
    cosineThreshold: form.cosineThreshold,
    rerankThreshold: form.rerankThreshold,
    rewriteMode: form.rewriteMode,
    rewriteCount: form.rewriteCount,
    enabled: form.enabled,
    isDefault: form.isDefault,
    remark: form.remark.trim() || undefined,
  }
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if (form.enableVector !== 1 && form.enableBm25 !== 1) {
    ElMessage.warning('至少启用向量或 BM25')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (formMode.value === 'create') {
      await createRetrievalStrategy(payload)
      ElMessage.success('已创建')
    } else {
      await updateRetrievalStrategy(editingId.value, payload)
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

async function setDefault(row: RetrievalStrategy) {
  if (!ensureJavaJwt()) return
  try {
    await updateRetrievalStrategy(row.id, {
      name: row.name,
      enableVector: row.enableVector,
      enableBm25: row.enableBm25,
      enableRrf: row.enableRrf,
      enableRerank: row.enableRerank,
      enableRewrite: row.enableRewrite,
      enableParentFill: row.enableParentFill,
      topK: row.topK,
      rerankTopN: row.rerankTopN,
      vectorTopK: row.vectorTopK,
      bm25TopK: row.bm25TopK,
      rrfK: row.rrfK,
      cosineThreshold: row.cosineThreshold,
      rerankThreshold: row.rerankThreshold,
      rewriteMode: row.rewriteMode,
      rewriteCount: row.rewriteCount,
      enabled: row.enabled ?? 1,
      isDefault: 1,
      remark: row.remark,
    })
    ElMessage.success(`已设「${row.name}」为默认`)
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '设默认失败')
  }
}

async function removeOne(row: RetrievalStrategy) {
  if (!ensureJavaJwt()) return
  try {
    await ElMessageBox.confirm(`确认删除检索策略「${row.name}」？`, '删除确认', { type: 'warning' })
    await deleteRetrievalStrategy(row.id)
    ElMessage.success('已删除')
    void load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function removeBatch() {
  if (!ensureJavaJwt()) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选策略')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedIds.value.length} 条？默认策略与被知识库引用的会跳过。`,
      '批量删除',
      { type: 'warning', confirmButtonText: '删除' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const res = await batchDeleteRetrievalStrategies(selectedIds.value)
    ElMessage.success(`已删除 ${res.deleted} 条`)
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>检索策略</h2>
        <p>七开关 + 余弦/重排双阈值；知识库绑定后，检索测试与问答按该策略执行。</p>
      </div>
      <div class="head-actions">
        <el-button round :loading="loading" @click="load">刷新</el-button>
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
      </div>
    </header>

    <div class="panel-body">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="query.name"
            clearable
            placeholder="请输入策略名称查询"
            style="width: 260px"
            @keyup.enter="search"
          />
          <el-button round @click="search">查询</el-button>
          <el-button round @click="resetFilters">重置</el-button>
        </div>
        <el-button type="primary" round :icon="Plus" @click="openCreate">新增</el-button>
      </div>

      <el-table
        :data="rows"
        v-loading="loading"
        stripe
        class="strategy-table"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="策略名称" min-width="168" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="name-cell">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用的能力" min-width="280">
          <template #default="{ row }">
            <div class="cap-tags">
              <span
                v-for="t in capabilityTags(row)"
                :key="t.key"
                class="cap-tag"
                :class="t.tone"
              >
                {{ t.text }}
              </span>
              <span v-if="!capabilityTags(row).length" class="muted">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="RRF k" width="88" align="center">
          <template #default="{ row }">{{ rrfDisplay(row) }}</template>
        </el-table-column>
        <el-table-column label="阈值" min-width="168">
          <template #default="{ row }">
            <div class="cap-tags">
              <span
                class="cap-tag"
                :class="row.cosineThreshold != null ? 'th-on' : 'th-off'"
              >
                余弦 {{ formatThreshold(row.cosineThreshold) }}
              </span>
              <span
                class="cap-tag"
                :class="row.rerankThreshold != null ? 'th-on' : 'th-off'"
              >
                重排 {{ formatThreshold(row.rerankThreshold) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" size="small" type="success" round effect="light">
              默认
            </el-tag>
            <span v-else class="muted">否</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="240" align="center">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button type="primary" size="small" round @click="openEdit(row)">编辑</el-button>
              <el-button size="small" round @click="goRetrievalTest(row)">试用</el-button>
              <el-button type="danger" size="small" round @click="removeOne(row)">删除</el-button>
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
      :title="formMode === 'create' ? '新建检索策略' : '编辑检索策略'"
      variant="form"
      width="720px"
      :close-on-click-modal="!saving"
    >
      <div class="app-modal-body strategy-form">
        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">0</span>
            <label>策略名称 <em>*</em></label>
          </div>
          <el-input v-model="form.name" maxlength="128" placeholder="如：基线-纯向量检索" />
        </div>

        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">1</span>
            <label>一、召回</label>
          </div>
          <div class="switch-row">
            <span>向量检索</span>
            <el-radio-group v-model="form.enableVector">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">关闭</el-radio>
            </el-radio-group>
            <el-input-number
              v-if="form.enableVector === 1"
              v-model="form.vectorTopK"
              :min="1"
              :max="100"
              controls-position="right"
            />
          </div>
          <div class="switch-row">
            <span>BM25 检索</span>
            <el-radio-group v-model="form.enableBm25">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">关闭</el-radio>
            </el-radio-group>
            <el-input-number
              v-if="form.enableBm25 === 1"
              v-model="form.bm25TopK"
              :min="1"
              :max="100"
              controls-position="right"
            />
          </div>
          <div v-if="showRrf" class="switch-row">
            <span>RRF 融合常数</span>
            <el-input-number v-model="form.rrfK" :min="1" :max="200" controls-position="right" />
          </div>
        </div>

        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">2</span>
            <label>二、精排</label>
          </div>
          <div class="switch-row">
            <span>重排</span>
            <el-radio-group v-model="form.enableRerank">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">关闭</el-radio>
            </el-radio-group>
            <el-input-number
              v-if="form.enableRerank === 1"
              v-model="form.rerankTopN"
              :min="1"
              :max="50"
              controls-position="right"
            />
          </div>
        </div>

        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">3</span>
            <label>三、查询改写</label>
          </div>
          <div class="switch-row">
            <span>查询改写</span>
            <el-radio-group v-model="form.enableRewrite">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">关闭</el-radio>
            </el-radio-group>
          </div>
          <template v-if="form.enableRewrite === 1">
            <div class="switch-row">
              <span>改写方式</span>
              <el-select v-model="form.rewriteMode" style="width: 220px">
                <el-option
                  v-for="o in REWRITE_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </div>
            <div
              v-if="form.rewriteMode === 'multi_query' || form.rewriteMode === 'all'"
              class="switch-row"
            >
              <span>扩展条数</span>
              <el-input-number
                v-model="form.rewriteCount"
                :min="1"
                :max="5"
                controls-position="right"
              />
            </div>
          </template>
        </div>

        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">4</span>
            <label>四、后处理</label>
          </div>
          <div class="switch-row">
            <span>父块回填</span>
            <el-radio-group v-model="form.enableParentFill">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">关闭</el-radio>
            </el-radio-group>
          </div>
          <div class="metric-row">
            <span>当前量纲</span>
            <el-tag round effect="light" type="success">{{ scoreMetricLabel }}</el-tag>
            <span class="hint">由上面的开关组合决定，下面只有这一档的阈值会生效</span>
          </div>
          <div class="switch-row stacked">
            <span>余弦相似度阈值</span>
            <el-input-number
              v-model="form.cosineThreshold"
              :min="0"
              :max="1"
              :step="0.01"
              :precision="3"
              controls-position="right"
              :disabled="scoreMetric !== 'cosine'"
            />
            <p class="hint">
              向量单路召回时生效。相关片段实测 0.46~0.77，完全无关 0.14~0.23，建议 0.3
            </p>
          </div>
          <div class="switch-row stacked">
            <span>重排相关分阈值</span>
            <el-input-number
              v-model="form.rerankThreshold"
              :min="0"
              :max="1"
              :step="0.01"
              :precision="3"
              controls-position="right"
              :disabled="scoreMetric !== 'rerank'"
            />
            <p class="hint">
              开了重排时生效。相关片段实测 0.14~0.20，完全无关约 0.006，建议 0.05
            </p>
          </div>
          <p v-if="scoreMetric === 'rrf'" class="warn">
            当前组合的最终分数是 RRF 名次分，只表示排序先后，不能和固定门槛比大小，本策略不会做阈值过滤。要让阈值生效，请打开重排。
          </p>
          <div class="switch-row">
            <span>设为默认</span>
            <el-radio-group v-model="form.isDefault">
              <el-radio :value="1">是</el-radio>
              <el-radio :value="0">否</el-radio>
            </el-radio-group>
          </div>
          <div class="switch-row stacked">
            <span>备注</span>
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="3"
              maxlength="512"
              placeholder="说明该策略适合的场景"
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
}
.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.panel-body {
  padding: 16px 20px 24px;
  display: grid;
  gap: 12px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.row-actions {
  display: inline-flex;
  gap: 6px;
  flex-wrap: nowrap;
  white-space: nowrap;
  justify-content: center;
}
.name-cell {
  font-weight: 600;
}
.muted {
  color: var(--ink-muted);
}
.cap-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.cap-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.5;
  white-space: nowrap;
  border: 1px solid transparent;
}
.cap-vector {
  color: #1d4ed8;
  background: #eff6ff;
  border-color: #bfdbfe;
}
.cap-bm25 {
  color: #15803d;
  background: #f0fdf4;
  border-color: #bbf7d0;
}
.cap-rerank {
  color: #c2410c;
  background: #fff7ed;
  border-color: #fed7aa;
}
.cap-rewrite {
  color: #be185d;
  background: #fdf2f8;
  border-color: #fbcfe8;
}
.cap-parent {
  color: #475569;
  background: #f8fafc;
  border-color: #e2e8f0;
}
.th-on {
  color: #15803d;
  background: #f0fdf4;
  border-color: #bbf7d0;
}
.th-off {
  color: #64748b;
  background: #f8fafc;
  border-color: #e2e8f0;
}
.strategy-form {
  display: grid;
  gap: 16px;
}
.switch-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.switch-row > span:first-child {
  min-width: 110px;
  font-weight: 600;
}
.switch-row.stacked {
  align-items: flex-start;
  flex-direction: column;
}
.metric-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 8px 0 12px;
}
.hint {
  margin: 0;
  color: var(--ink-muted);
  font-size: 12px;
  line-height: 1.5;
}
.warn {
  margin: 0 0 12px;
  padding: 8px 12px;
  border-radius: 10px;
  background: rgba(230, 162, 60, 0.12);
  color: #a16207;
  font-size: 12px;
  line-height: 1.5;
}
</style>
