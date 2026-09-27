<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document as DocumentIcon, Delete, Plus, UploadFilled } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteDocuments,
  chunkDocument,
  clearDocumentVectors,
  deleteDocument,
  downloadDocument,
  getDocumentParsedText,
  listChunkStrategies,
  listDocuments,
  listKnowledgeBases,
  reuploadDocument,
  updateDocument,
  uploadDocument,
  vectorizeDocument,
  type ChunkStrategy,
  type DocumentParsedText,
  type KnowledgeBase,
  type KnowledgeDocument,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'
import { formatDateTime } from '@/utils/datetime'

const router = useRouter()
const route = useRoute()
const loadingList = ref(false)
const loadingKb = ref(false)
const uploading = ref(false)
const uploadOpen = ref(false)
const actingId = ref('')

const bases = ref<KnowledgeBase[]>([])
const chunkStrategies = ref<ChunkStrategy[]>([])
const rows = ref<KnowledgeDocument[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
const batchDeleting = ref(false)

const query = reactive({
  knowledgeBaseId: '' as string,
  title: '',
  parseStatus: '' as string,
  vectorIssue: '' as string,
  page: 1,
  pageSize: 8,
})

const form = reactive({
  knowledgeBaseId: 'kb_default',
})
const selectedFile = ref<File | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const chunkOpen = ref(false)
const chunking = ref(false)
const chunkDoc = ref<KnowledgeDocument | null>(null)
const chunkForm = reactive({ chunkStrategyId: '' as string })

const editOpen = ref(false)
const editing = ref(false)
const editDoc = ref<KnowledgeDocument | null>(null)
const editForm = reactive({ title: '' })

const reuploadOpen = ref(false)
const reuploading = ref(false)
const reuploadDoc = ref<KnowledgeDocument | null>(null)
const reuploadFile = ref<File | null>(null)
const reuploadInputRef = ref<HTMLInputElement | null>(null)

const parseOpen = ref(false)
const parsing = ref(false)
const parseResult = ref<DocumentParsedText | null>(null)

const kbOptions = computed(() =>
  (Array.isArray(bases.value) ? bases.value : []).map((b) => ({
    value: b.id,
    label: `${b.name}（${b.id}）`,
  })),
)

const strategyOptions = computed(() =>
  (Array.isArray(chunkStrategies.value) ? chunkStrategies.value : []).map((s) => ({
    value: s.id,
    label: `${s.name}${s.isDefault === 1 ? '（默认）' : ''} · ${s.strategyType}`,
  })),
)

const activeKb = computed(() => bases.value.find((b) => b.id === query.knowledgeBaseId) || null)
const activeKbLabel = computed(() => {
  if (!query.knowledgeBaseId) return ''
  return activeKb.value?.name || query.knowledgeBaseId
})

function syncRouteQuery(extra: Record<string, string | undefined> = {}) {
  const next: Record<string, string> = {}
  if (query.knowledgeBaseId) next.knowledgeBaseId = query.knowledgeBaseId
  if (query.title.trim()) next.title = query.title.trim()
  if (query.parseStatus) next.parseStatus = query.parseStatus
  if (query.vectorIssue) next.vectorIssue = query.vectorIssue
  for (const [k, v] of Object.entries(extra)) {
    if (v) next[k] = v
    else delete next[k]
  }
  delete next.upload
  delete next.documentId
  const cur = route.query
  const same =
    (cur.knowledgeBaseId || '') === (next.knowledgeBaseId || '') &&
    (cur.title || '') === (next.title || '') &&
    (cur.parseStatus || '') === (next.parseStatus || '') &&
    (cur.vectorIssue || '') === (next.vectorIssue || '') &&
    !cur.upload &&
    !cur.documentId
  if (!same) {
    void router.replace({ path: '/knowledge/document', query: next })
  }
}

function applyRouteQuery() {
  query.knowledgeBaseId =
    typeof route.query.knowledgeBaseId === 'string' ? route.query.knowledgeBaseId : ''
  query.title = typeof route.query.title === 'string' ? route.query.title : ''
  query.parseStatus = typeof route.query.parseStatus === 'string' ? route.query.parseStatus : ''
  query.vectorIssue = typeof route.query.vectorIssue === 'string' ? route.query.vectorIssue : ''
}

const STATUS_LABEL: Record<string, string> = {
  UPLOADED: '已上传',
  PARSING: '解析中',
  PARSED: '已解析',
  CHUNKED: '已切分',
  EMBEDDING: '向量化中',
  READY: '已就绪',
  FAILED: '失败',
}

const statusOptions = [
  { value: 'UPLOADED', label: '已上传' },
  { value: 'PARSED', label: '已解析' },
  { value: 'CHUNKED', label: '已切分' },
  { value: 'EMBEDDING', label: '向量化中' },
  { value: 'READY', label: '已就绪' },
  { value: 'FAILED', label: '失败' },
]

function statusLabel(code?: string) {
  if (!code) return '—'
  return STATUS_LABEL[code] || code
}

function parseStatusTagType(code?: string): 'success' | 'danger' | 'warning' | 'info' {
  if (code === 'READY' || code === 'PARSED' || code === 'CHUNKED') return 'success'
  if (code === 'FAILED') return 'danger'
  if (code === 'PARSING' || code === 'EMBEDDING') return 'warning'
  return 'info'
}

function vectorStatusLabel(code?: string) {
  if (!code) return '未向量化'
  const map: Record<string, string> = {
    READY: '已完成',
    EMPTY: '未向量化',
    PARTIAL: '部分完成',
    FAILED: '失败',
    PENDING: '进行中',
  }
  return map[code] || code
}

function vectorTagType(code?: string) {
  if (code === 'READY') return 'success'
  if (code === 'PARTIAL') return 'warning'
  if (code === 'FAILED') return 'danger'
  return 'info'
}

function vectorIssueLabel(code?: string) {
  switch ((code || '').toUpperCase()) {
    case 'PENDING':
      return '待向量化片段'
    case 'FAILED':
      return '向量失败片段'
    case 'PARTIAL':
      return '向量异常（待处理/失败）'
    default:
      return code || ''
  }
}

function fileTypeLabel(type?: string) {
  if (!type) return '—'
  const t = type.toUpperCase()
  if (t === 'MD' || t === 'MARKDOWN') return 'MARKDOWN'
  if (t === 'DOCX' || t === 'WORD') return 'WORD'
  return t
}

function formatFileSize(size?: number) {
  if (size == null || Number.isNaN(size)) return '—'
  // 使用不换行空格，避免「数字 / 单位」被表格挤成两行
  if (size < 1024) return `${size}\u00A0B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}\u00A0KB`
  return `${(size / 1024 / 1024).toFixed(2)}\u00A0MB`
}

/** 悬停展示完整大小（含原始字节） */
function formatFileSizeTip(size?: number) {
  if (size == null || Number.isNaN(size)) return '—'
  const pretty = formatFileSize(size)
  return `${pretty}（${size.toLocaleString()} 字节）`
}

/** 列表展示：前 12 字 + …；完整名靠 hover tooltip */
const TITLE_DISPLAY_LEN = 12

function fullDocName(row: { title?: string; fileName?: string }) {
  return (row.title || row.fileName || '').trim() || '—'
}

function shortDocName(row: { title?: string; fileName?: string }) {
  const full = fullDocName(row)
  if (full === '—') return full
  if (full.length <= TITLE_DISPLAY_LEN) return full
  return `${full.slice(0, TITLE_DISPLAY_LEN)}…`
}

function formatTime(value?: string) {
  return formatDateTime(value)
}

function canChunk(row: KnowledgeDocument) {
  return !!row.id && row.parseStatus !== 'EMBEDDING'
}

function canVectorize(row: KnowledgeDocument) {
  return row.parseStatus === 'CHUNKED' || row.parseStatus === 'READY' || (row.segmentCount ?? 0) > 0
}

function canClearVectors(row: KnowledgeDocument) {
  if (row.parseStatus === 'EMBEDDING') return false
  return (
    row.parseStatus === 'READY' ||
    row.vectorStatus === 'READY' ||
    row.vectorStatus === 'PARTIAL' ||
    (row.vectorDoneCount ?? 0) > 0
  )
}

function ensureJavaJwt(): boolean {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录 Java（Gateway:8080 + Service:8081）获取 JWT')
    router.push({ path: '/login', query: { redirect: '/knowledge/document' } })
    return false
  }
  return true
}

async function loadBases() {
  if (!ensureJavaJwt()) return
  loadingKb.value = true
  try {
    const list = await listKnowledgeBases()
    bases.value = Array.isArray(list) ? list : []
    if (!form.knowledgeBaseId && bases.value.length) {
      form.knowledgeBaseId = bases.value[0].id
    }
  } catch (e) {
    bases.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载知识库失败')
  } finally {
    loadingKb.value = false
  }
}

async function loadStrategies() {
  if (!ensureJavaJwt()) return
  try {
    const list = await listChunkStrategies()
    chunkStrategies.value = Array.isArray(list) ? list : []
  } catch {
    chunkStrategies.value = []
  }
}

async function loadList() {
  if (!ensureJavaJwt()) return
  loadingList.value = true
  try {
    const page = await listDocuments({
      knowledgeBaseId: query.knowledgeBaseId || undefined,
      title: query.title.trim() || undefined,
      parseStatus: query.parseStatus || undefined,
      vectorIssue: query.vectorIssue || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    rows.value = page.records || []
    total.value = page.total ?? 0
    selectedIds.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载文件列表失败')
  } finally {
    loadingList.value = false
  }
}

function onSelectionChange(selection: KnowledgeDocument[]) {
  selectedIds.value = selection.map((r) => r.id).filter(Boolean)
}

function search() {
  query.page = 1
  syncRouteQuery()
  void loadList()
}

function resetQuery() {
  query.knowledgeBaseId = ''
  query.title = ''
  query.parseStatus = ''
  query.vectorIssue = ''
  query.page = 1
  query.pageSize = 8
  syncRouteQuery()
  void loadList()
}

function clearKbFilter() {
  query.knowledgeBaseId = ''
  query.page = 1
  syncRouteQuery()
  void loadList()
}

function clearVectorIssue() {
  query.vectorIssue = ''
  query.page = 1
  syncRouteQuery()
  void loadList()
}

function goKnowledgeBase() {
  if (query.knowledgeBaseId) {
    router.push({
      path: '/knowledge/base',
      query: { name: activeKb.value?.name || undefined, highlight: query.knowledgeBaseId },
    })
    return
  }
  router.push('/knowledge/base')
}

function goKbFromRow(row: KnowledgeDocument) {
  if (!row.knowledgeBaseId) return
  router.push({
    path: '/knowledge/base',
    query: { name: row.knowledgeBaseName || undefined, highlight: row.knowledgeBaseId },
  })
}

function goSegments(row: KnowledgeDocument) {
  router.push({
    path: '/knowledge/segment',
    query: {
      knowledgeBaseId: row.knowledgeBaseId || undefined,
      documentId: row.id,
    },
  })
}

function goRetrievalTest(row: KnowledgeDocument) {
  router.push({
    path: '/knowledge/retrieval-test',
    query: {
      knowledgeBaseId: row.knowledgeBaseId || undefined,
      documentId: row.id,
    },
  })
}

function onListPageChange(payload: { page: number; pageSize: number }) {
  query.page = payload.page
  query.pageSize = payload.pageSize
  void loadList()
}

async function openParse(row: KnowledgeDocument) {
  if (!ensureJavaJwt()) return
  parsing.value = true
  parseResult.value = null
  parseOpen.value = true
  ElMessage.info('大文件/扫描件解析可能较慢，请稍候…')
  try {
    parseResult.value = await getDocumentParsedText(row.id)
    const hit = rows.value.find((r) => r.id === row.id)
    if (hit && parseResult.value.charCount != null) {
      hit.charCount = parseResult.value.charCount
      if (hit.parseStatus === 'UPLOADED') hit.parseStatus = 'PARSED'
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : '解析失败'
    ElMessage.error(
      /timeout|timed out|Timeout|超时/i.test(msg)
        ? '解析超时：请稍后重试，或先对较小文件验证；扫描件仅预览前几页'
        : msg,
    )
    parseOpen.value = false
  } finally {
    parsing.value = false
  }
}

function openUpload() {
  if (!ensureJavaJwt()) return
  selectedFile.value = null
  form.knowledgeBaseId =
    query.knowledgeBaseId ||
    bases.value.find((b) => b.id === 'kb_default')?.id ||
    bases.value[0]?.id ||
    'kb_default'
  uploadOpen.value = true
  void (async () => {
    if (!bases.value.length) await loadBases()
  })()
}

function onFilePick(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] || null
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  const file = event.dataTransfer?.files?.[0]
  if (file) selectedFile.value = file
}

async function startUpload() {
  if (!ensureJavaJwt()) return
  if (!form.knowledgeBaseId) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    await uploadDocument(selectedFile.value, { knowledgeBaseId: form.knowledgeBaseId })
    ElMessage.success('上传成功（仅存盘，请再切分 / 向量化）')
    uploadOpen.value = false
    query.knowledgeBaseId = form.knowledgeBaseId
    query.page = 1
    syncRouteQuery()
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

function openChunk(row: KnowledgeDocument) {
  if (!ensureJavaJwt()) return
  chunkDoc.value = row
  const kb = bases.value.find((b) => b.id === row.knowledgeBaseId)
  chunkForm.chunkStrategyId =
    row.chunkStrategyId ||
    kb?.chunkStrategyId ||
    chunkStrategies.value.find((s) => s.isDefault === 1)?.id ||
    chunkStrategies.value[0]?.id ||
    ''
  chunkOpen.value = true
  void loadStrategies()
}

async function submitChunk() {
  if (!chunkDoc.value) return
  chunking.value = true
  actingId.value = chunkDoc.value.id
  try {
    await chunkDocument(chunkDoc.value.id, chunkForm.chunkStrategyId || undefined)
    ElMessage.success('切分完成：片段为「待向量化」，请再点「向量化」')
    chunkOpen.value = false
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '切分失败')
  } finally {
    chunking.value = false
    actingId.value = ''
  }
}

async function doVectorize(row: KnowledgeDocument) {
  if (!ensureJavaJwt()) return
  actingId.value = row.id
  try {
    await vectorizeDocument(row.id)
    ElMessage.success('向量化完成')
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '向量化失败')
  } finally {
    actingId.value = ''
  }
}

async function doClearVectors(row: KnowledgeDocument) {
  if (!ensureJavaJwt()) return
  try {
    await ElMessageBox.confirm('将清除该文件全部向量，片段保留，可重新向量化。继续？', '清除向量', {
      type: 'warning',
      confirmButtonText: '清除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await clearDocumentVectors(row.id)
    ElMessage.success('已清除向量')
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '清除失败')
  } finally {
    actingId.value = ''
  }
}

async function doDownload(row: KnowledgeDocument) {
  if (!ensureJavaJwt()) return
  try {
    await downloadDocument(row.id, row.fileName || row.title || 'download')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '下载失败')
  }
}

function openEdit(row: KnowledgeDocument) {
  editDoc.value = row
  editForm.title = row.title || row.fileName || ''
  editOpen.value = true
}

async function submitEdit() {
  if (!editDoc.value) return
  if (!editForm.title.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  editing.value = true
  try {
    await updateDocument(editDoc.value.id, { title: editForm.title.trim() })
    ElMessage.success('已保存')
    editOpen.value = false
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    editing.value = false
  }
}

async function openReupload(row: KnowledgeDocument) {
  if (!ensureJavaJwt()) return
  if (canClearVectors(row) || row.parseStatus === 'READY') {
    try {
      await ElMessageBox.confirm(
        '该文件已向量化。重新上传将清除旧向量与片段并替换文件，确认继续？',
        '重新上传',
        { type: 'warning', confirmButtonText: '继续', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }
  reuploadDoc.value = row
  reuploadFile.value = null
  reuploadOpen.value = true
}

function onReuploadPick(event: Event) {
  const input = event.target as HTMLInputElement
  reuploadFile.value = input.files?.[0] || null
}

async function submitReupload() {
  if (!reuploadDoc.value || !reuploadFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  reuploading.value = true
  try {
    await reuploadDocument(reuploadDoc.value.id, reuploadFile.value)
    ElMessage.success('重新上传成功')
    reuploadOpen.value = false
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '重新上传失败')
  } finally {
    reuploading.value = false
  }
}

async function doDelete(row: KnowledgeDocument) {
  if (!ensureJavaJwt()) return
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.title || row.fileName}」？将同步清除向量与片段。`,
      '删除文件',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await deleteDocument(row.id)
    ElMessage.success('已删除')
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  } finally {
    actingId.value = ''
  }
}

async function removeBatch() {
  if (!ensureJavaJwt()) return
  let ids = [...selectedIds.value]
  if (!ids.length) {
    ElMessage.warning('请先勾选文件')
    return
  }

  // 当前页全选且还有更多筛选结果时，询问是否删全部筛选
  const selectedAllOnPage =
    rows.value.length > 0 && ids.length >= rows.value.length && total.value > rows.value.length
  if (selectedAllOnPage) {
    try {
      await ElMessageBox.confirm(
        `当前页已全选。筛选条件下共有 ${total.value} 个文件，是否删除全部筛选结果？\n选「取消」则仅删除当前页勾选的 ${ids.length} 个。`,
        '删除范围',
        {
          type: 'warning',
          confirmButtonText: `删除全部 ${total.value} 个`,
          cancelButtonText: `仅删当前 ${ids.length} 个`,
          distinguishCancelAndClose: true,
        },
      )
      // 确认：拉取筛选下全部 id
      const allIds: string[] = []
      const pageSize = 100
      const pages = Math.max(1, Math.ceil(total.value / pageSize))
      for (let p = 1; p <= pages; p++) {
        const page = await listDocuments({
          knowledgeBaseId: query.knowledgeBaseId || undefined,
          title: query.title.trim() || undefined,
          parseStatus: query.parseStatus || undefined,
          vectorIssue: query.vectorIssue || undefined,
          page: p,
          pageSize,
        })
        for (const r of page.records || []) {
          if (r.id) allIds.push(r.id)
        }
      }
      ids = allIds
    } catch (action) {
      if (action === 'close') return
      // cancel = 仅删当前页勾选，继续用 ids
    }
  }

  try {
    await ElMessageBox.confirm(
      `确认删除 ${ids.length} 个文件？将同步清除向量与片段。`,
      '批量删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  batchDeleting.value = true
  try {
    let deleted = 0
    try {
      const res = await batchDeleteDocuments(ids)
      deleted = res.deleted ?? 0
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e || '')
      // 向量清理失败 / 向量化冲突等：直接提示，勿静默逐条删掩盖原因
      if (/502|409|向量|向量化|BAD_GATEWAY|CONFLICT/i.test(msg)) {
        ElMessage.error(msg || '批量删除失败')
        await loadList()
        return
      }
      deleted = 0
      for (const id of ids) {
        try {
          await deleteDocument(id)
          deleted++
        } catch {
          // 单条失败继续
        }
      }
    }
    if (deleted <= 0) {
      ElMessage.error('批量删除失败')
    } else if (deleted < ids.length) {
      ElMessage.warning(`已删除 ${deleted}/${ids.length} 个，其余请重试`)
    } else {
      ElMessage.success(`已删除 ${deleted} 个`)
    }
    await loadList()
  } finally {
    batchDeleting.value = false
  }
}

async function bootstrap() {
  applyRouteQuery()
  await Promise.all([loadBases(), loadStrategies()])
  await loadList()
  if (route.query.upload === '1') {
    openUpload()
    syncRouteQuery()
  }
}

watch(
  () => [
    route.query.knowledgeBaseId,
    route.query.title,
    route.query.parseStatus,
    route.query.vectorIssue,
    route.query.upload,
  ],
  async () => {
    if (route.path !== '/knowledge/document') return
    const prevKb = query.knowledgeBaseId
    const prevTitle = query.title
    const prevStatus = query.parseStatus
    const prevIssue = query.vectorIssue
    applyRouteQuery()
    const changed =
      prevKb !== query.knowledgeBaseId ||
      prevTitle !== query.title ||
      prevStatus !== query.parseStatus ||
      prevIssue !== query.vectorIssue
    if (changed) {
      query.page = 1
      await loadList()
    }
    if (route.query.upload === '1') {
      openUpload()
      syncRouteQuery()
    }
  },
)

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <div class="doc-page">
    <section class="panel">
      <header class="panel-head">
        <div>
          <h2>文件管理</h2>
          <p>
            上传仅存盘并绑定知识库；请按「解析 → 切分 → 向量化」分步操作。片段可在
            <button type="button" class="text-link" @click="router.push('/knowledge/segment')">
              片段管理
            </button>
            中维护。
          </p>
        </div>
        <div class="head-actions">
          <el-button round :loading="loadingList" @click="loadList">刷新</el-button>
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
          <el-button type="primary" round :icon="Plus" @click="openUpload">上传文件</el-button>
        </div>
      </header>

      <div class="panel-body">
        <div v-if="query.knowledgeBaseId || query.vectorIssue" class="kb-banner">
          <span>
            <template v-if="query.knowledgeBaseId">
              当前知识库：<b>{{ activeKbLabel }}</b>
              <code>{{ query.knowledgeBaseId }}</code>
            </template>
            <template v-if="query.vectorIssue">
              <span class="banner-sep">·</span>
              向量筛选：<b>{{ vectorIssueLabel(query.vectorIssue) }}</b>
            </template>
          </span>
          <div class="kb-banner-actions">
            <el-button v-if="query.knowledgeBaseId" size="small" round @click="goKnowledgeBase">
              查看知识库
            </el-button>
            <el-button v-if="query.vectorIssue" size="small" round @click="clearVectorIssue">
              清除向量筛选
            </el-button>
            <el-button v-if="query.knowledgeBaseId" size="small" round @click="clearKbFilter">
              清除库筛选
            </el-button>
          </div>
        </div>

        <div class="filters">
          <el-select
            v-model="query.knowledgeBaseId"
            clearable
            filterable
            placeholder="请选择知识库"
            style="width: 220px"
            :loading="loadingKb"
            @change="search"
          >
            <el-option
              v-for="opt in kbOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-input
            v-model="query.title"
            clearable
            placeholder="请输入文件名称查询"
            style="width: 220px"
            @keyup.enter="search"
          />
          <el-select
            v-model="query.parseStatus"
            clearable
            placeholder="请选择状态"
            style="width: 160px"
          >
            <el-option
              v-for="s in statusOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
          <el-button type="primary" round @click="search">查询</el-button>
          <el-button round @click="resetQuery">重置</el-button>
        </div>

        <el-table
          :data="rows"
          row-key="id"
          v-loading="loadingList"
          stripe
          empty-text="暂无文件，请点击右上角「上传文件」"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column label="文档名称" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tooltip
                placement="top"
                :disabled="fullDocName(row).length <= TITLE_DISPLAY_LEN"
                :show-after="200"
                popper-class="doc-name-tooltip"
              >
                <template #content>
                  <div class="doc-name-tooltip-body">{{ fullDocName(row) }}</div>
                </template>
                <span class="title-cell">{{ shortDocName(row) }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="所属知识库" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">
              <button type="button" class="kb-name-link" @click="goKbFromRow(row)">
                {{ row.knowledgeBaseName || row.knowledgeBaseId || '—' }}
              </button>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="56" align="center">
            <template #default="{ row }">{{ fileTypeLabel(row.fileType) }}</template>
          </el-table-column>
          <el-table-column label="大小" width="96" align="right" class-name="col-nowrap">
            <template #default="{ row }">
              <el-tooltip
                :content="formatFileSizeTip(row.fileSize)"
                placement="top"
                :show-after="200"
              >
                <span class="cell-nowrap size-cell">{{ formatFileSize(row.fileSize) }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="解析" width="86" align="center">
            <template #default="{ row }">
              <el-tag size="small" round effect="light" :type="parseStatusTagType(row.parseStatus)">
                {{ statusLabel(row.parseStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="向量" width="86" align="center">
            <template #default="{ row }">
              <el-tag size="small" round effect="light" :type="vectorTagType(row.vectorStatus)">
                {{ vectorStatusLabel(row.vectorStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="字符" width="68" align="right">
            <template #default="{ row }">{{ row.charCount ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="上传时间" width="172" class-name="col-nowrap">
            <template #default="{ row }">
              <span class="cell-nowrap time-cell">{{ formatTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="312" align="center" class-name="col-actions">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button type="primary" size="small" round @click="openParse(row)">解析</el-button>
                <el-button
                  size="small"
                  round
                  :disabled="!canChunk(row)"
                  :loading="actingId === row.id && chunking"
                  @click="openChunk(row)"
                >
                  切分
                </el-button>
                <el-button
                  size="small"
                  round
                  :disabled="!canVectorize(row)"
                  :loading="actingId === row.id"
                  @click="doVectorize(row)"
                >
                  向量化
                </el-button>
                <el-dropdown trigger="click">
                  <el-button size="small" round>更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item :disabled="!canClearVectors(row)" @click="doClearVectors(row)">
                        清除向量
                      </el-dropdown-item>
                      <el-dropdown-item @click="doDownload(row)">下载</el-dropdown-item>
                      <el-dropdown-item @click="openReupload(row)">重新上传</el-dropdown-item>
                      <el-dropdown-item @click="openEdit(row)">修改</el-dropdown-item>
                      <el-dropdown-item @click="goSegments(row)">查看片段</el-dropdown-item>
                      <el-dropdown-item @click="goRetrievalTest(row)">检索测试</el-dropdown-item>
                      <el-dropdown-item divided @click="doDelete(row)">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
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
      v-model="parseOpen"
      title="解析预览"
      variant="detail"
      width="860px"
      :close-on-click-modal="!parsing"
    >
      <div v-loading="parsing" class="parse-preview">
        <div v-if="parseResult" class="parse-meta">
          <span>{{ parseResult.title || parseResult.fileName || '—' }}</span>
          <el-tag size="small" round>{{ fileTypeLabel(parseResult.fileType) }}</el-tag>
          <span class="parse-chars">{{ parseResult.charCount ?? 0 }} 字符</span>
        </div>
        <p v-if="parseResult?.hint" class="parse-hint">{{ parseResult.hint }}</p>
        <pre v-if="parseResult" class="parse-text">{{ parseResult.text || '（空文本）' }}</pre>
        <p v-else-if="!parsing" class="parse-empty">暂无解析结果</p>
      </div>
      <template #footer>
        <el-button round @click="parseOpen = false">关闭</el-button>
      </template>
    </AppModal>

    <AppModal
      v-model="uploadOpen"
      title="上传文件"
      variant="form"
      width="720px"
      :close-on-click-modal="!uploading"
    >
      <div class="app-modal-body">
        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">1</span>
            <label>知识库 <em>*</em></label>
          </div>
          <el-select
            v-model="form.knowledgeBaseId"
            filterable
            placeholder="选择知识库"
            style="width: 100%"
            :loading="loadingKb"
            popper-class="app-modal-select-popper"
          >
            <el-option
              v-for="opt in kbOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <p class="app-modal-hint">必须绑定知识库；上传后不会自动切分或向量化。</p>
        </div>

        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">2</span>
            <label>选择文件 <em>*</em></label>
          </div>
          <div class="app-modal-drop" @dragover.prevent @drop="onDrop">
            <el-icon :size="40"><UploadFilled /></el-icon>
            <p>拖拽到此处，或点击选择</p>
            <p class="sub">支持 PDF / TXT / MD / DOCX</p>
            <el-button round @click="fileInputRef?.click()">选择文件</el-button>
            <input
              ref="fileInputRef"
              class="app-modal-hidden-file"
              type="file"
              accept=".pdf,.txt,.md,.docx,.markdown,.xlsx,.pptx"
              @change="onFilePick"
            />
            <div v-if="selectedFile" class="app-modal-picked">
              <el-icon><DocumentIcon /></el-icon>
              <span>{{ selectedFile.name }}</span>
              <em>{{ (selectedFile.size / 1024).toFixed(1) }} KB</em>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button round :disabled="uploading" @click="uploadOpen = false">取消</el-button>
        <el-button
          type="primary"
          round
          :loading="uploading"
          :disabled="!selectedFile || !form.knowledgeBaseId"
          @click="startUpload"
        >
          {{ uploading ? '上传中…' : '上传' }}
        </el-button>
      </template>
    </AppModal>

    <AppModal
      v-model="chunkOpen"
      title="切分文件"
      variant="form"
      width="560px"
      :close-on-click-modal="!chunking"
    >
      <div class="app-modal-body">
        <p class="app-modal-hint" style="margin-top: 0">
          文件：{{ chunkDoc?.title || chunkDoc?.fileName || '—' }}。重切分会清除旧片段与向量。
        </p>
        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">1</span>
            <label>切分策略</label>
          </div>
          <el-select
            v-model="chunkForm.chunkStrategyId"
            filterable
            clearable
            placeholder="默认使用知识库绑定策略"
            style="width: 100%"
            popper-class="app-modal-select-popper"
          >
            <el-option
              v-for="opt in strategyOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button round :disabled="chunking" @click="chunkOpen = false">取消</el-button>
        <el-button type="primary" round :loading="chunking" @click="submitChunk">开始切分</el-button>
      </template>
    </AppModal>

    <AppModal
      v-model="editOpen"
      title="修改文件"
      variant="form"
      width="480px"
      :close-on-click-modal="!editing"
    >
      <div class="app-modal-body">
        <div class="app-modal-step">
          <div class="app-modal-step-title">
            <span class="app-modal-step-no">1</span>
            <label>文档名称 <em>*</em></label>
          </div>
          <el-input v-model="editForm.title" maxlength="200" show-word-limit />
        </div>
      </div>
      <template #footer>
        <el-button round :disabled="editing" @click="editOpen = false">取消</el-button>
        <el-button type="primary" round :loading="editing" @click="submitEdit">保存</el-button>
      </template>
    </AppModal>

    <AppModal
      v-model="reuploadOpen"
      title="重新上传"
      variant="form"
      width="560px"
      :close-on-click-modal="!reuploading"
    >
      <div class="app-modal-body">
        <p class="app-modal-hint" style="margin-top: 0">
          将替换「{{ reuploadDoc?.title || reuploadDoc?.fileName }}」的原文件。
        </p>
        <el-button round @click="reuploadInputRef?.click()">选择新文件</el-button>
        <input
          ref="reuploadInputRef"
          class="app-modal-hidden-file"
          type="file"
          accept=".pdf,.txt,.md,.docx,.markdown,.xlsx,.pptx"
          @change="onReuploadPick"
        />
        <div v-if="reuploadFile" class="app-modal-picked" style="margin-top: 12px">
          <el-icon><DocumentIcon /></el-icon>
          <span>{{ reuploadFile.name }}</span>
        </div>
      </div>
      <template #footer>
        <el-button round :disabled="reuploading" @click="reuploadOpen = false">取消</el-button>
        <el-button
          type="primary"
          round
          :loading="reuploading"
          :disabled="!reuploadFile"
          @click="submitReupload"
        >
          确认上传
        </el-button>
      </template>
    </AppModal>
  </div>
</template>

<style scoped>
.doc-page {
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
  max-width: 640px;
}
.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.row-actions {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
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
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
}
.size-cell {
  cursor: default;
}
.time-cell {
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  letter-spacing: 0;
  overflow: visible;
  text-overflow: clip;
}
:deep(.col-nowrap .cell) {
  white-space: nowrap;
}
:deep(.col-actions .cell) {
  padding-left: 6px;
  padding-right: 6px;
}
.parse-preview {
  min-height: 280px;
  padding: 8px 4px 12px;
  display: grid;
  gap: 12px;
}
.parse-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
}
.parse-chars {
  color: var(--ink-muted);
  font-weight: 500;
  font-size: 13px;
}
.parse-hint {
  margin: 0;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(245, 158, 11, 0.12);
  color: #92400e;
  font-size: 13px;
  line-height: 1.45;
}
.parse-text {
  margin: 0;
  max-height: min(58vh, 560px);
  overflow: auto;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink, #1f2937);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.65;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.parse-empty {
  color: var(--ink-muted);
  margin: 40px 0;
  text-align: center;
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
.text-link {
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
.kb-banner {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid rgba(75, 116, 61, 0.2);
  background: rgba(127, 173, 108, 0.12);
  font-size: 13px;
}
.kb-banner b {
  font-weight: 650;
}
.banner-sep {
  margin: 0 6px;
  color: var(--ink-muted);
}
.kb-banner code {
  margin-left: 8px;
  font-size: 11px;
  color: var(--ink-muted);
  background: transparent;
}
.kb-banner-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.kb-name-link {
  border: none;
  background: transparent;
  color: var(--matcha-700, #3d6b2e);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  text-align: left;
}
.kb-name-link:hover {
  text-decoration: underline;
}
.title-cell {
  display: inline-block;
  max-width: 100%;
  line-height: 1.4;
  font-size: 13px;
  white-space: nowrap;
  cursor: default;
}
</style>

<!-- tooltip teleport 到 body，需非 scoped -->
<style>
.doc-name-tooltip.el-popper {
  max-width: min(420px, 70vw) !important;
}
.doc-name-tooltip .doc-name-tooltip-body {
  max-width: min(400px, 68vw);
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.55;
  font-size: 12px;
}
</style>
