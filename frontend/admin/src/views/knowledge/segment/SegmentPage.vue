<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteSegments,
  deleteSegment,
  listDocuments,
  listKnowledgeBases,
  listSegments,
  updateSegment,
  type KnowledgeBase,
  type KnowledgeDocument,
  type KnowledgeSegment,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const loadingKb = ref(false)
const loadingDocs = ref(false)
const saving = ref(false)

const bases = ref<KnowledgeBase[]>([])
const documents = ref<KnowledgeDocument[]>([])
const rows = ref<KnowledgeSegment[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])

const query = reactive({
  knowledgeBaseId: '' as string,
  documentId: '' as string,
  segmentType: '' as string,
  vectorStatus: '' as string,
  keyword: '',
  page: 1,
  pageSize: 8,
})

const editOpen = ref(false)
const editRow = ref<KnowledgeSegment | null>(null)
const editContent = ref('')

const TYPE_OPTIONS = [
  { value: 'CHUNK', label: '普通块' },
  { value: 'CHILD', label: '子块' },
  { value: 'PARENT', label: '父块' },
]

const VECTOR_OPTIONS = [
  { value: 'PENDING', label: '待向量化' },
  { value: 'DONE', label: '已向量化' },
  { value: 'FAILED', label: '失败' },
  { value: 'SKIPPED', label: '跳过' },
]

const kbOptions = computed(() =>
  bases.value.map((b) => ({ value: b.id, label: `${b.name}（${b.id}）` })),
)

const docOptions = computed(() =>
  documents.value.map((d) => ({
    value: d.id,
    label: d.title || d.fileName || d.id,
  })),
)

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    router.push({ path: '/login', query: { redirect: '/knowledge/segment' } })
    return false
  }
  return true
}

function typeLabel(t?: string) {
  return TYPE_OPTIONS.find((o) => o.value === t)?.label || t || '—'
}

function vectorLabel(code?: string) {
  const map: Record<string, string> = {
    DONE: '已向量化',
    PENDING: '待向量化',
    SKIPPED: '跳过',
    FAILED: '失败',
  }
  return map[code || ''] || code || '—'
}

function vectorTagType(code?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (code === 'DONE') return 'success'
  if (code === 'PENDING') return 'warning'
  if (code === 'FAILED') return 'danger'
  return 'info'
}

function segId(row: KnowledgeSegment) {
  return row.id || row.segmentId || ''
}

function preview(content?: string) {
  const text = (content || '').replace(/\s+/g, ' ').trim()
  if (!text) return '（空）'
  return text.length > 160 ? `${text.slice(0, 160)}…` : text
}

function applyRouteQuery() {
  query.knowledgeBaseId =
    typeof route.query.knowledgeBaseId === 'string' ? route.query.knowledgeBaseId : ''
  query.documentId = typeof route.query.documentId === 'string' ? route.query.documentId : ''
  query.segmentType = typeof route.query.segmentType === 'string' ? route.query.segmentType : ''
  query.vectorStatus =
    typeof route.query.vectorStatus === 'string' ? route.query.vectorStatus : ''
  query.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : ''
}

function syncRouteQuery() {
  const next: Record<string, string> = {}
  if (query.knowledgeBaseId) next.knowledgeBaseId = query.knowledgeBaseId
  if (query.documentId) next.documentId = query.documentId
  if (query.segmentType) next.segmentType = query.segmentType
  if (query.vectorStatus) next.vectorStatus = query.vectorStatus
  if (query.keyword.trim()) next.keyword = query.keyword.trim()
  void router.replace({ path: '/knowledge/segment', query: next })
}

async function loadBases() {
  if (!ensureJavaJwt()) return
  loadingKb.value = true
  try {
    const list = await listKnowledgeBases()
    bases.value = Array.isArray(list) ? list : []
  } catch (e) {
    bases.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载知识库失败')
  } finally {
    loadingKb.value = false
  }
}

async function loadDocuments() {
  if (!ensureJavaJwt()) return
  loadingDocs.value = true
  try {
    const pageSize = 200
    const page = await listDocuments({
      knowledgeBaseId: query.knowledgeBaseId || undefined,
      page: 1,
      pageSize,
    })
    documents.value = page.records || []
    if ((page.total ?? 0) > pageSize) {
      ElMessage.warning(`文档下拉仅展示前 ${pageSize} 条（共 ${page.total}）`)
    }
  } catch (e) {
    documents.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载文档失败')
  } finally {
    loadingDocs.value = false
  }
}

async function loadList() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await listSegments({
      knowledgeBaseId: query.knowledgeBaseId || undefined,
      documentId: query.documentId || undefined,
      segmentType: query.segmentType || undefined,
      vectorStatus: query.vectorStatus || undefined,
      keyword: query.keyword.trim() || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    rows.value = page.records || []
    total.value = page.total ?? 0
    selectedIds.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载片段失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  syncRouteQuery()
  void loadList()
}

function resetQuery() {
  query.knowledgeBaseId = ''
  query.documentId = ''
  query.segmentType = ''
  query.vectorStatus = ''
  query.keyword = ''
  query.page = 1
  syncRouteQuery()
  void loadDocuments()
  void loadList()
}

async function onKbChange() {
  query.documentId = ''
  query.page = 1
  syncRouteQuery()
  await loadDocuments()
  await loadList()
}

function onListPageChange(payload: { page: number; pageSize: number }) {
  query.page = payload.page
  query.pageSize = payload.pageSize
  void loadList()
}

function onSelectionChange(selection: KnowledgeSegment[]) {
  selectedIds.value = selection.map((r) => segId(r)).filter(Boolean)
}

function openEdit(row: KnowledgeSegment) {
  editRow.value = row
  editContent.value = row.content || ''
  editOpen.value = true
}

async function submitEdit() {
  if (!editRow.value) return
  if (!editContent.value.trim()) {
    ElMessage.warning('内容不能为空')
    return
  }
  saving.value = true
  try {
    await updateSegment(segId(editRow.value), { content: editContent.value.trim() })
    ElMessage.success('已保存（若已向量化会清除该段向量）')
    editOpen.value = false
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function doDelete(row: KnowledgeSegment) {
  try {
    await ElMessageBox.confirm('确认删除该片段？', '删除片段', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteSegment(segId(row))
    ElMessage.success('已删除')
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function doBatchDelete() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选片段')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个片段？`, '批量删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const n = await batchDeleteSegments(selectedIds.value)
    ElMessage.success(`已删除 ${n} 条`)
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  }
}

async function bootstrap() {
  applyRouteQuery()
  await loadBases()
  await loadDocuments()
  await loadList()
}

watch(
  () => [route.query.knowledgeBaseId, route.query.documentId],
  async () => {
    if (route.path !== '/knowledge/segment') return
    applyRouteQuery()
    await loadDocuments()
    query.page = 1
    await loadList()
  },
)

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <div class="seg-page">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>片段管理</h2>
          <p>
            切分后片段默认为「待向量化」，需在文件管理手动向量化；编辑已向量化片段会清除该段向量。
          </p>
        </div>
        <div class="head-actions">
          <el-button round :loading="loading" @click="loadList">刷新</el-button>
          <el-button
            type="danger"
            round
            :icon="Delete"
            :disabled="!selectedIds.length"
            @click="doBatchDelete"
          >
            批量删除
          </el-button>
        </div>
      </header>

      <div class="panel-body">
        <div class="filters">
          <el-select
            v-model="query.knowledgeBaseId"
            clearable
            filterable
            placeholder="请选择知识库"
            style="width: 220px"
            :loading="loadingKb"
            @change="onKbChange"
          >
            <el-option
              v-for="opt in kbOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-select
            v-model="query.documentId"
            clearable
            filterable
            placeholder="请选择文档"
            style="width: 220px"
            :loading="loadingDocs"
            @change="search"
          >
            <el-option
              v-for="opt in docOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-select
            v-model="query.segmentType"
            clearable
            placeholder="片段类型"
            style="width: 140px"
            @change="search"
          >
            <el-option
              v-for="t in TYPE_OPTIONS"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
          <el-select
            v-model="query.vectorStatus"
            clearable
            placeholder="向量化状态"
            style="width: 140px"
            @change="search"
          >
            <el-option
              v-for="t in VECTOR_OPTIONS"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="请输入片段内容"
            style="width: 220px"
            @keyup.enter="search"
          />
          <el-button type="primary" round @click="search">查询</el-button>
          <el-button round @click="resetQuery">重置</el-button>
        </div>

        <el-table
          :data="rows"
          v-loading="loading"
          stripe
          empty-text="暂无片段；请先在文件管理中切分"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column label="序号" width="72" align="center">
            <template #default="{ $index }">
              {{ (query.page - 1) * query.pageSize + $index + 1 }}
            </template>
          </el-table-column>
          <el-table-column label="类型" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" round effect="light">{{ typeLabel(row.segmentType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="片段内容" min-width="280">
            <template #default="{ row }">
              <el-tooltip
                placement="top"
                :show-after="280"
                popper-class="seg-content-tooltip"
              >
                <template #content>
                  <div class="seg-content-tooltip-body">{{ row.content || '（空）' }}</div>
                </template>
                <div class="content-preview">{{ preview(row.content) }}</div>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="字符数" width="90" align="right">
            <template #default="{ row }">
              {{ row.contentTokens ?? row.content?.length ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column label="页码" width="72" align="center">
            <template #default="{ row }">{{ row.pageNo ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="向量化状态" width="112" align="center">
            <template #default="{ row }">
              <el-tag size="small" round effect="light" :type="vectorTagType(row.vectorStatus)">
                {{ vectorLabel(row.vectorStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right" align="center">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button size="small" round :icon="Edit" @click="openEdit(row)">编辑</el-button>
                <el-button size="small" round type="danger" :icon="Delete" @click="doDelete(row)">
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
          @change="onListPageChange"
        />
      </div>
    </section>

    <AppModal
      v-model="editOpen"
      title="编辑片段"
      variant="form"
      width="720px"
      :close-on-click-modal="!saving"
    >
      <div class="app-modal-body">
        <el-input
          v-model="editContent"
          type="textarea"
          :rows="14"
          maxlength="20000"
          show-word-limit
          placeholder="片段内容"
        />
      </div>
      <template #footer>
        <el-button round :disabled="saving" @click="editOpen = false">取消</el-button>
        <el-button type="primary" round :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.seg-page {
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
.panel-head h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.panel-head p {
  margin: 0;
  color: var(--ink-muted);
  font-size: 13px;
  max-width: 620px;
}
.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.panel-body {
  padding: 20px 24px 28px;
  display: grid;
  gap: 16px;
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
  justify-content: center;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}
.content-preview {
  line-height: 1.45;
  color: var(--ink-soft, #445);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  cursor: default;
}
</style>

<style>
.seg-content-tooltip {
  max-width: min(520px, 80vw) !important;
}
.seg-content-tooltip-body {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
  max-height: 320px;
  overflow: auto;
  font-size: 13px;
}
</style>
