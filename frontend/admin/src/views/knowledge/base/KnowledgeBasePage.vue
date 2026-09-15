<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteKnowledgeBases,
  createKnowledgeBase,
  createKnowledgeCategory,
  deleteKnowledgeBase,
  deleteKnowledgeCategory,
  listChunkStrategies,
  listKnowledgeBaseVectorDocuments,
  listKnowledgeCategories,
  listRetrievalStrategies,
  pageKnowledgeBases,
  pageKnowledgeCategories,
  updateKnowledgeBase,
  updateKnowledgeCategory,
  type ChunkStrategy,
  type KnowledgeBase,
  type KnowledgeCategory,
  type KnowledgeDocumentVector,
  type RetrievalStrategy,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'
import { formatDateTime } from '@/utils/datetime'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const rows = ref<KnowledgeBase[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
const highlightId = ref('')

const query = reactive({
  name: '',
  status: '' as number | '',
  category: '' as string,
  page: 1,
  pageSize: 8,
})

const categoryOptions = ref<KnowledgeCategory[]>([])
const chunkStrategyOptions = ref<ChunkStrategy[]>([])
const retrievalStrategyOptions = ref<RetrievalStrategy[]>([])

const catManageOpen = ref(false)
const loadingCats = ref(false)
const catRows = ref<KnowledgeCategory[]>([])
const catTotal = ref(0)
const catQuery = reactive({
  keyword: '',
  status: '' as number | '',
  page: 1,
  pageSize: 8,
})

const catFormOpen = ref(false)
const catFormMode = ref<'create' | 'edit'>('create')
const catEditingId = ref('')
const catSaving = ref(false)
const catForm = reactive({
  code: '',
  name: '',
  sortNo: 0,
  status: 1 as number,
})

function applyRouteQuery() {
  const name = typeof route.query.name === 'string' ? route.query.name : ''
  const highlight = typeof route.query.highlight === 'string' ? route.query.highlight : ''
  if (name) query.name = name
  highlightId.value = highlight
}

function rowClassName({ row }: { row: KnowledgeBase }) {
  return highlightId.value && row.id === highlightId.value ? 'is-highlight' : ''
}

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const form = reactive({
  name: '',
  category: '',
  description: '',
  status: 1 as number,
  chunkStrategyId: '' as string,
  retrievalStrategyId: '' as string,
})

const vectorOpen = ref(false)
const vectorRow = ref<KnowledgeBase | null>(null)
const loadingVectorDocs = ref(false)
const vectorDocs = ref<KnowledgeDocumentVector[]>([])

/** 弹窗汇总与下方文档明细对齐（不含 PG 孤儿） */
const vectorSummary = computed(() => {
  let segs = 0
  let done = 0
  let pending = 0
  let failed = 0
  for (const d of vectorDocs.value) {
    segs += Number(d.segmentCount ?? 0)
    done += Number(d.vectorDoneCount ?? 0)
    pending += Number(d.vectorPendingCount ?? 0)
    if ((d.parseStatus || '').toUpperCase() === 'FAILED') failed += 1
    else failed += Number(d.vectorFailedCount ?? 0)
  }
  const status =
    failed > 0 && segs <= 0
      ? 'FAILED'
      : pending > 0
        ? 'PARTIAL'
        : segs > 0 && done >= segs
          ? 'READY'
          : segs > 0
            ? 'PARTIAL'
            : 'EMPTY'
  return { segs, done, pending, failed, status }
})

const statusOptions = [
  { value: '' as const, label: '全部状态' },
  { value: 1 as const, label: '正常' },
  { value: 0 as const, label: '停用' },
]

function ensureJavaJwt(): boolean {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录 Java（Gateway:8080 + Service:8081）获取 JWT')
    router.push({ path: '/login', query: { redirect: '/knowledge/base' } })
    return false
  }
  return true
}

function formatTime(value?: string) {
  return formatDateTime(value)
}

function statusLabel(status?: number) {
  if (status === 1) return '正常'
  if (status === 0) return '停用'
  return '—'
}

function vectorLabel(code?: string) {
  switch (code) {
    case 'READY':
      return '已就绪'
    case 'PARTIAL':
      return '部分完成'
    case 'FAILED':
      return '失败'
    case 'EMPTY':
      return '无片段'
    default:
      return code || '—'
  }
}

function vectorTagType(code?: string) {
  if (code === 'READY') return 'success'
  if (code === 'PARTIAL') return 'warning'
  if (code === 'FAILED') return 'danger'
  return 'info'
}

async function loadCategoryOptions() {
  try {
    categoryOptions.value = await listKnowledgeCategories()
  } catch (e) {
    categoryOptions.value = []
    ElMessage.warning(e instanceof Error ? e.message : '加载分类列表失败，请确认 Service 已启动')
  }
}

async function loadChunkStrategyOptions() {
  try {
    chunkStrategyOptions.value = await listChunkStrategies()
  } catch {
    chunkStrategyOptions.value = []
  }
  try {
    retrievalStrategyOptions.value = await listRetrievalStrategies()
  } catch {
    retrievalStrategyOptions.value = []
  }
}

function categoryLabel(code?: string) {
  if (!code) return '—'
  const hit = categoryOptions.value.find((c) => c.code === code)
  return hit ? hit.name : code
}

function chunkStrategyLabel(id?: string) {
  if (!id) return '—'
  const hit = chunkStrategyOptions.value.find((s) => s.id === id)
  return hit ? hit.name : id
}

function retrievalStrategyLabel(id?: string) {
  if (!id) return '—'
  const hit = retrievalStrategyOptions.value.find((s) => s.id === id)
  return hit ? hit.name : id
}

async function loadList() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pageKnowledgeBases({
      name: query.name.trim() || undefined,
      status: query.status,
      category: query.category || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    rows.value = page.records || []
    total.value = page.total ?? 0
    selectedIds.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载知识库失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  void loadList()
}

function resetQuery() {
  query.name = ''
  query.status = ''
  query.category = ''
  query.page = 1
  query.pageSize = 8
  void loadList()
}

function onPageChange(payload: { page: number; pageSize: number }) {
  query.page = payload.page
  query.pageSize = payload.pageSize
  void loadList()
}

function onSelectionChange(selection: KnowledgeBase[]) {
  selectedIds.value = selection.map((r) => r.id)
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = ''
  form.name = ''
  form.category = ''
  form.description = ''
  form.status = 1
  form.chunkStrategyId =
    chunkStrategyOptions.value.find((s) => s.isDefault === 1)?.id ||
    chunkStrategyOptions.value.find((s) => s.id === 'cs_recursive')?.id ||
    chunkStrategyOptions.value[0]?.id ||
    'cs_recursive'
  form.retrievalStrategyId =
    retrievalStrategyOptions.value.find((s) => s.isDefault === 1)?.id ||
    retrievalStrategyOptions.value.find((s) => s.id === 'rs_baseline_vector')?.id ||
    retrievalStrategyOptions.value[0]?.id ||
    'rs_baseline_vector'
  formOpen.value = true
  void loadCategoryOptions()
  void loadChunkStrategyOptions()
}

function openEdit(row: KnowledgeBase) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.name = row.name
  form.category = row.category || ''
  form.description = row.description || ''
  form.status = row.status ?? 1
  form.chunkStrategyId = row.chunkStrategyId || ''
  form.retrievalStrategyId = row.retrievalStrategyId || ''
  formOpen.value = true
  void loadCategoryOptions()
  void loadChunkStrategyOptions()
}

async function loadCatList() {
  if (!ensureJavaJwt()) return
  loadingCats.value = true
  try {
    const page = await pageKnowledgeCategories({
      keyword: catQuery.keyword.trim() || undefined,
      status: catQuery.status,
      page: catQuery.page,
      pageSize: catQuery.pageSize,
    })
    catRows.value = page.records || []
    catTotal.value = page.total ?? 0
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载分类失败')
  } finally {
    loadingCats.value = false
  }
}

function openCatManage() {
  if (!ensureJavaJwt()) return
  catManageOpen.value = true
  catQuery.page = 1
  void loadCatList()
}

function openCatCreate() {
  catFormMode.value = 'create'
  catEditingId.value = ''
  catForm.code = ''
  catForm.name = ''
  catForm.sortNo = 0
  catForm.status = 1
  catFormOpen.value = true
}

function openCatEdit(row: KnowledgeCategory) {
  catFormMode.value = 'edit'
  catEditingId.value = row.id
  catForm.code = row.code
  catForm.name = row.name
  catForm.sortNo = row.sortNo ?? 0
  catForm.status = row.status ?? 1
  catFormOpen.value = true
}

async function submitCatForm() {
  if (!ensureJavaJwt()) return
  if (!catForm.code.trim() || !catForm.name.trim()) {
    ElMessage.warning('请填写分类编码与名称')
    return
  }
  catSaving.value = true
  try {
    const payload = {
      code: catForm.code.trim(),
      name: catForm.name.trim(),
      sortNo: catForm.sortNo,
      status: catForm.status,
    }
    if (catFormMode.value === 'create') {
      await createKnowledgeCategory(payload)
      ElMessage.success('已创建分类')
    } else {
      await updateKnowledgeCategory(catEditingId.value, payload)
      ElMessage.success('已保存分类')
    }
    catFormOpen.value = false
    void loadCatList()
    void loadCategoryOptions()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存分类失败')
  } finally {
    catSaving.value = false
  }
}

async function removeCat(row: KnowledgeCategory) {
  if (!ensureJavaJwt()) return
  try {
    await ElMessageBox.confirm(
      `确认删除分类「${row.name}」（${row.code}）？若仍有知识库引用将无法删除。`,
      '删除分类',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteKnowledgeCategory(row.id)
    ElMessage.success('已删除分类')
    void loadCatList()
    void loadCategoryOptions()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除分类失败')
  }
}

function onCatPageChange(payload: { page: number; pageSize: number }) {
  catQuery.page = payload.page
  catQuery.pageSize = payload.pageSize
  void loadCatList()
}

async function openVector(row: KnowledgeBase) {
  vectorRow.value = row
  vectorOpen.value = true
  loadingVectorDocs.value = true
  vectorDocs.value = []
  try {
    vectorDocs.value = await listKnowledgeBaseVectorDocuments(row.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载文档向量状态失败')
  } finally {
    loadingVectorDocs.value = false
  }
}

function goDocsWithFilter(opts: {
  parseStatus?: string
  vectorIssue?: string
  documentId?: string
}) {
  if (!vectorRow.value) return
  vectorOpen.value = false
  const query: Record<string, string> = { knowledgeBaseId: vectorRow.value.id }
  if (opts.parseStatus) query.parseStatus = opts.parseStatus
  if (opts.vectorIssue) query.vectorIssue = opts.vectorIssue
  if (opts.documentId) query.documentId = opts.documentId
  router.push({ path: '/knowledge/document', query })
}

function goDocDetail(doc: KnowledgeDocumentVector) {
  goDocsWithFilter({ documentId: doc.documentId })
}

function parseStatusLabel(code?: string) {
  const map: Record<string, string> = {
    UPLOADED: '已上传',
    PARSING: '解析中',
    READY: '已就绪',
    FAILED: '失败',
  }
  return (code && map[code]) || code || '—'
}

async function submitForm() {
  if (!ensureJavaJwt()) return
  if (!form.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      category: form.category.trim() || undefined,
      description: form.description.trim() || undefined,
      status: form.status,
      chunkStrategyId: form.chunkStrategyId || undefined,
      retrievalStrategyId: form.retrievalStrategyId || undefined,
    }
    if (formMode.value === 'create') {
      await createKnowledgeBase(payload)
      ElMessage.success('已创建知识库')
    } else {
      await updateKnowledgeBase(editingId.value, payload)
      ElMessage.success('已保存')
    }
    formOpen.value = false
    void loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeOne(row: KnowledgeBase) {
  if (!ensureJavaJwt()) return
  try {
    await ElMessageBox.confirm(
      `确认删除知识库「${row.name}」？将软删除其下文档与片段，并尝试清理向量。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteKnowledgeBase(row.id)
    ElMessage.success('已删除')
    void loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function removeBatch() {
  if (!ensureJavaJwt()) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选知识库')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedIds.value.length} 个知识库？`,
      '批量删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const res = await batchDeleteKnowledgeBases(selectedIds.value)
    ElMessage.success(`已删除 ${res.deleted} 个`)
    void loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  }
}

function goDocuments(row: KnowledgeBase) {
  router.push({
    path: '/knowledge/document',
    query: { knowledgeBaseId: row.id },
  })
}

function goRetrievalTest(row: KnowledgeBase) {
  router.push({
    path: '/knowledge/retrieval-test',
    query: { knowledgeBaseId: row.id },
  })
}

watch(
  () => [route.query.name, route.query.highlight],
  () => {
    if (route.path !== '/knowledge/base') return
    applyRouteQuery()
    query.page = 1
    void loadList()
  },
)

onMounted(() => {
  applyRouteQuery()
  void loadCategoryOptions()
  void loadChunkStrategyOptions()
  void loadList()
})
</script>

<template>
  <div class="kb-page">
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>知识库管理</h2>
        <p>
          维护知识库元数据；文件与片段归属知识库（N:1）。可点击文档数进入文件管理，或从「向量状态」下钻。分类请先在「分类管理」维护后再选择。
        </p>
      </div>
      <div class="head-actions">
        <el-button round @click="openCatManage">分类管理</el-button>
        <el-button round :loading="loading" @click="loadList">刷新</el-button>
      </div>
    </header>

    <div class="panel-body">
      <div class="filters">
        <el-input
          v-model="query.name"
          clearable
          placeholder="请输入知识库名称查询"
          style="width: 240px"
          @keyup.enter="search"
        />
        <el-select v-model="query.category" clearable filterable placeholder="全部分类" style="width: 160px">
          <el-option
            v-for="c in categoryOptions"
            :key="c.code"
            :label="c.name"
            :value="c.code"
          />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="请选择状态" style="width: 140px">
          <el-option
            v-for="s in statusOptions"
            :key="String(s.value)"
            :label="s.label"
            :value="s.value"
          />
        </el-select>
        <el-button type="primary" round @click="search">查询</el-button>
        <el-button round @click="resetQuery">重置</el-button>
      </div>

      <div class="toolbar">
        <el-button type="primary" round :icon="Plus" @click="openCreate">新增</el-button>
        <el-button type="danger" plain round :icon="Delete" @click="removeBatch">批量删除</el-button>
      </div>

      <el-table
        :data="rows"
        v-loading="loading"
        stripe
        empty-text="暂无知识库，请点击「新增」"
        :row-class-name="rowClassName"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="42" />
        <el-table-column prop="name" label="知识库名称" min-width="110" show-overflow-tooltip />
        <el-table-column label="分类" width="88" show-overflow-tooltip>
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column label="切分策略" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ chunkStrategyLabel(row.chunkStrategyId) }}</template>
        </el-table-column>
        <el-table-column label="检索策略" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ retrievalStrategyLabel(row.retrievalStrategyId) }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="100" show-overflow-tooltip />
        <el-table-column label="文档" width="64" align="center">
          <template #default="{ row }">
            <button type="button" class="count-link" @click="goDocuments(row)">
              {{ row.documentCount ?? 0 }}
            </button>
          </template>
        </el-table-column>
        <el-table-column label="片段" width="64" align="center">
          <template #default="{ row }">{{ row.segmentCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="72" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              round
              effect="light"
              :type="row.status === 1 ? 'success' : 'info'"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="172" class-name="col-nowrap">
          <template #default="{ row }">
            <span class="cell-nowrap time-cell">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="248" align="center" class-name="col-actions">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button type="success" size="small" round @click="openVector(row)">向量状态</el-button>
              <el-button size="small" round @click="goRetrievalTest(row)">检索测试</el-button>
              <el-button type="primary" size="small" circle :icon="Edit" @click="openEdit(row)" />
              <el-button type="danger" size="small" circle :icon="Delete" @click="removeOne(row)" />
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
  </section>

    <AppModal
      v-model="formOpen"
      :title="formMode === 'create' ? '新增知识库' : '编辑知识库'"
      variant="form"
      width="720px"
      :close-on-click-modal="!saving"
    >
      <div class="app-modal-body">
        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">1</span>
            <label>基本信息 <em>*</em></label>
          </div>
          <div class="form-grid">
            <label>名称</label>
            <el-input v-model="form.name" maxlength="128" placeholder="知识库名称" />
            <label>分类</label>
            <el-select
              v-model="form.category"
              clearable
              filterable
              teleported
              placeholder="请选择分类（可选）"
              style="width: 100%"
              popper-class="app-modal-select-popper"
              :loading="categoryOptions.length === 0"
            >
              <el-option
                v-for="c in categoryOptions"
                :key="c.code"
                :label="`${c.name}（${c.code}）`"
                :value="c.code"
              />
            </el-select>
            <label>切分策略</label>
            <el-select
              v-model="form.chunkStrategyId"
              filterable
              clearable
              teleported
              placeholder="选择切分策略"
              style="width: 100%"
              popper-class="app-modal-select-popper"
            >
              <el-option
                v-for="s in chunkStrategyOptions"
                :key="s.id"
                :label="`${s.name}${s.isDefault === 1 ? '（默认）' : ''} · ${s.strategyType}`"
                :value="s.id"
              />
            </el-select>
            <label>检索策略</label>
            <el-select
              v-model="form.retrievalStrategyId"
              filterable
              clearable
              teleported
              placeholder="选择检索策略"
              style="width: 100%"
              popper-class="app-modal-select-popper"
            >
              <el-option
                v-for="s in retrievalStrategyOptions"
                :key="s.id"
                :label="`${s.name}${s.isDefault === 1 ? '（默认）' : ''}`"
                :value="s.id"
              />
            </el-select>
            <label>描述</label>
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="8"
              maxlength="512"
              show-word-limit
              resize="vertical"
              placeholder="简要说明知识库用途"
              class="kb-desc-input"
            />
            <label>状态</label>
            <el-radio-group v-model="form.status">
              <el-radio :value="1">正常</el-radio>
              <el-radio :value="0">停用</el-radio>
            </el-radio-group>
          </div>
          <p class="app-modal-hint">
            分类来自「分类管理」；切分策略来自「切分策略」页。新建未选策略时后端默认 cs_recursive。
          </p>
        </div>
      </div>
      <template #footer>
        <el-button round :disabled="saving" @click="formOpen = false">取消</el-button>
        <el-button type="primary" round :loading="saving" @click="submitForm">
          {{ formMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </AppModal>

    <AppModal v-model="catManageOpen" title="分类管理" variant="form" width="780px">
      <div class="app-modal-body">
        <div class="cat-toolbar">
          <el-input
            v-model="catQuery.keyword"
            clearable
            placeholder="名称 / 编码"
            style="width: 200px"
            @keyup.enter="
              catQuery.page = 1;
              loadCatList();
            "
          />
          <el-select
            v-model="catQuery.status"
            clearable
            placeholder="状态"
            style="width: 120px"
            teleported
            popper-class="app-modal-select-popper"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button
            type="primary"
            round
            @click="
              catQuery.page = 1;
              loadCatList();
            "
          >
            查询
          </el-button>
          <el-button type="primary" round :icon="Plus" @click="openCatCreate">新增分类</el-button>
        </div>
        <el-table :data="catRows" v-loading="loadingCats" stripe size="small" empty-text="暂无分类">
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="code" label="编码" width="140" />
          <el-table-column label="排序" width="72" align="center">
            <template #default="{ row }">{{ row.sortNo ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="引用" width="72" align="center">
            <template #default="{ row }">{{ row.refCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="88" align="center">
            <template #default="{ row }">
              <el-tag size="small" round effect="light" :type="row.status === 1 ? 'success' : 'info'">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" circle :icon="Edit" @click="openCatEdit(row)" />
              <el-button type="danger" size="small" circle :icon="Delete" @click="removeCat(row)" />
            </template>
          </el-table-column>
        </el-table>
        <AppPagination
          compact
          :total="catTotal"
          :page="catQuery.page"
          :page-size="catQuery.pageSize"
          @change="onCatPageChange"
        />
      </div>
      <template #footer>
        <el-button round @click="catManageOpen = false">关闭</el-button>
      </template>
    </AppModal>

    <AppModal
      v-model="catFormOpen"
      :title="catFormMode === 'create' ? '新增分类' : '编辑分类'"
      variant="form"
      width="480px"
      :close-on-click-modal="!catSaving"
    >
      <div class="app-modal-body">
        <div class="app-modal-step">
          <div class="form-grid">
            <label>编码 <em style="color: #c45656">*</em></label>
            <el-input
              v-model="catForm.code"
              maxlength="64"
              placeholder="如 enterprise（字母开头）"
            />
            <label>名称 <em style="color: #c45656">*</em></label>
            <el-input v-model="catForm.name" maxlength="128" placeholder="展示名称" />
            <label>排序</label>
            <el-input-number v-model="catForm.sortNo" :min="0" :max="9999" />
            <label>状态</label>
            <el-radio-group v-model="catForm.status">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">停用</el-radio>
            </el-radio-group>
          </div>
          <p class="app-modal-hint">编码写入知识库 category 字段；被引用时仍可改名/排序，改编码会同步更新引用。</p>
        </div>
      </div>
      <template #footer>
        <el-button round :disabled="catSaving" @click="catFormOpen = false">取消</el-button>
        <el-button type="primary" round :loading="catSaving" @click="submitCatForm">
          {{ catFormMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </AppModal>

    <AppModal v-model="vectorOpen" title="向量状态" variant="form" width="860px">
      <div v-if="vectorRow" class="app-modal-body">
        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">i</span>
            <label>{{ vectorRow.name }}</label>
          </div>
          <div class="vector-facts">
            <div>
              <span>汇总</span>
              <el-tag size="small" round effect="light" :type="vectorTagType(vectorSummary.status)">
                {{ vectorLabel(vectorSummary.status) }}
              </el-tag>
            </div>
            <button type="button" class="vector-fact-btn" @click="goDocsWithFilter({})">
              <span>片段总数</span><b>{{ vectorSummary.segs }}</b>
            </button>
            <button type="button" class="vector-fact-btn" @click="goDocsWithFilter({ parseStatus: 'READY' })">
              <span>已向量化 DONE</span><b>{{ vectorSummary.done }}</b>
            </button>
            <button
              type="button"
              class="vector-fact-btn"
              @click="goDocsWithFilter({ vectorIssue: 'PENDING' })"
            >
              <span>待处理 PENDING</span><b>{{ vectorSummary.pending }}</b>
            </button>
            <button
              type="button"
              class="vector-fact-btn"
              @click="goDocsWithFilter({ parseStatus: 'FAILED' })"
            >
              <span>失败 FAILED</span><b>{{ vectorSummary.failed }}</b>
            </button>
          </div>
          <div class="vector-actions">
            <el-button type="primary" round size="small" @click="goDocsWithFilter({})">查看该库文档</el-button>
            <el-button round size="small" @click="goDocsWithFilter({ parseStatus: 'FAILED' })">
              入库失败文档
            </el-button>
            <el-button round size="small" @click="goDocsWithFilter({ vectorIssue: 'PARTIAL' })">
              向量异常文档
            </el-button>
          </div>
          <p class="app-modal-hint">点击统计或下方文档可跳转文件管理。</p>
        </div>

        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">2</span>
            <label>文档向量明细</label>
          </div>
          <el-table
            :data="vectorDocs"
            v-loading="loadingVectorDocs"
            stripe
            size="small"
            max-height="320"
            empty-text="该库暂无文档"
          >
            <el-table-column label="文档标题" min-width="180">
              <template #default="{ row }">
                <button type="button" class="doc-link" @click="goDocDetail(row)">{{ row.title }}</button>
              </template>
            </el-table-column>
            <el-table-column label="入库状态" width="96">
              <template #default="{ row }">{{ parseStatusLabel(row.parseStatus) }}</template>
            </el-table-column>
            <el-table-column label="片段" width="64" align="center">
              <template #default="{ row }">{{ row.segmentCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="DONE" width="64" align="center">
              <template #default="{ row }">{{ row.vectorDoneCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="PENDING" width="80" align="center">
              <template #default="{ row }">{{ row.vectorPendingCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="FAILED" width="72" align="center">
              <template #default="{ row }">{{ row.vectorFailedCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="向量" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" round effect="light" :type="vectorTagType(row.vectorStatus)">
                  {{ vectorLabel(row.vectorStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="88" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" round @click="goDocDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button round @click="vectorOpen = false">关闭</el-button>
        <el-button type="primary" round @click="goDocsWithFilter({})">打开文件管理</el-button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.kb-page {
  min-height: calc(100vh - var(--header-height) - 48px);
}
.panel {
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--surface);
  box-shadow: var(--shadow);
  overflow: visible;
  min-height: calc(100vh - var(--header-height) - 48px);
}
.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(120deg, rgba(127, 173, 108, 0.14), transparent 55%);
  border-radius: 20px 20px 0 0;
}
.panel-head h2 { margin: 0 0 6px; font-size: 22px; }
.panel-head p { margin: 0; color: var(--ink-muted); font-size: 13px; max-width: 640px; }
.head-actions { display: flex; gap: 8px; flex-shrink: 0; }
.panel-body { padding: 20px 24px 28px; display: grid; gap: 14px; }
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.cat-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}
.row-actions {
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 4px;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
}
.row-actions :deep(.el-button) {
  margin: 0;
}
.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.cell-nowrap {
  white-space: nowrap;
  display: inline-block;
}
.time-cell {
  font-variant-numeric: tabular-nums;
  font-size: 13px;
}
:deep(.col-nowrap .cell) {
  white-space: nowrap;
}
:deep(.col-actions .cell) {
  padding-left: 6px;
  padding-right: 6px;
}
.count-link {
  border: none;
  background: transparent;
  color: var(--matcha-700, #3d6b2e);
  font: inherit;
  font-weight: 650;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
  padding: 0;
}
.count-link:hover {
  color: var(--matcha-600, #4b743d);
}
:deep(.el-table .is-highlight > td) {
  background: rgba(127, 173, 108, 0.16) !important;
}
.form-grid {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 14px 12px;
  align-items: start;
}
.form-grid label {
  padding-top: 8px;
  font-size: 13px;
  color: var(--ink-muted);
}
.kb-desc-input :deep(textarea) {
  min-height: 160px;
  line-height: 1.5;
}
.vector-facts {
  display: grid;
  gap: 10px;
}
.vector-facts > div,
.vector-fact-btn {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #ffffff;
  border: 1px solid rgba(75, 116, 61, 0.12);
  width: 100%;
  box-sizing: border-box;
  font: inherit;
  color: inherit;
  text-align: left;
}
.vector-fact-btn {
  cursor: pointer;
}
.vector-fact-btn:hover {
  background: rgba(127, 173, 108, 0.16);
}
.vector-facts span {
  font-size: 12px;
  color: var(--ink-muted);
}
.vector-facts b {
  font-size: 14px;
  font-weight: 650;
}
.vector-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.doc-link {
  border: none;
  background: transparent;
  color: var(--matcha-700, #3d6b2e);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  text-align: left;
}
.doc-link:hover {
  text-decoration: underline;
}
</style>
