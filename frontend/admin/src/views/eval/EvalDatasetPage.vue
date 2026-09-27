<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteEvalCases,
  countPendingDislikes,
  createEvalCase,
  createEvalDataset,
  deleteEvalCase,
  deleteEvalDataset,
  generateEvalCasesFromDoc,
  importEvalCasesFromDislikes,
  listDocuments,
  listEvalDatasets,
  listKnowledgeBases,
  pageEvalCases,
  updateEvalCase,
  type EvalCase,
  type EvalDataset,
  type KnowledgeBase,
  type KnowledgeDocument,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const loading = ref(false)
const datasets = ref<EvalDataset[]>([])
const datasetId = ref('')
const cases = ref<EvalCase[]>([])
const total = ref(0)
const selectedCaseIds = ref<string[]>([])
const batchDeletingCases = ref(false)
const pendingDislikes = ref(0)
const kbOptions = ref<KnowledgeBase[]>([])
const docOptions = ref<KnowledgeDocument[]>([])

const query = reactive({
  sourceType: '' as string,
  question: '',
  page: 1,
  pageSize: 10,
})

const datasetOpen = ref(false)
const datasetForm = reactive({ name: '', knowledgeBaseId: '', description: '' })
const datasetSaving = ref(false)

const caseOpen = ref(false)
const caseMode = ref<'create' | 'edit'>('create')
const editingCaseId = ref('')
const caseForm = reactive({
  question: '',
  expectedAnswer: '',
  includeInEval: 1 as number,
  remark: '',
})
const caseSaving = ref(false)

const genOpen = ref(false)
const genForm = reactive({ knowledgeBaseId: '', documentId: '', count: 5 })
const genSaving = ref(false)
const docLoading = ref(false)

const sourceOpen = ref(false)
const sourceCase = ref<EvalCase | null>(null)

const currentDataset = computed(() => datasets.value.find((d) => d.id === datasetId.value))

function ensureJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT')
    return false
  }
  return true
}

function sourceLabel(t?: string) {
  if (t === 'FROM_DOC') return '文档生成'
  if (t === 'FROM_DISLIKE') return '点踩沉淀'
  return '手工录入'
}

function sourceTagType(t?: string) {
  if (t === 'FROM_DOC') return 'success'
  if (t === 'FROM_DISLIKE') return 'warning'
  return 'info'
}

async function loadDatasets(preferId?: string) {
  if (!ensureJwt()) return
  try {
    datasets.value = (await listEvalDatasets()) || []
    const prefer = preferId || datasetId.value
    if (prefer && datasets.value.some((d) => d.id === prefer)) {
      datasetId.value = prefer
    } else if (!datasetId.value && datasets.value.length) {
      datasetId.value = datasets.value[0].id
    } else if (datasets.value.length === 0) {
      datasetId.value = ''
      cases.value = []
      total.value = 0
    }
  } catch (e) {
    datasets.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载评测集失败')
  }
}

async function loadCases() {
  if (!ensureJwt() || !datasetId.value) return
  loading.value = true
  try {
    const page = await pageEvalCases(datasetId.value, {
      sourceType: query.sourceType || undefined,
      question: query.question || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    cases.value = page?.records || []
    total.value = page?.total || 0
    selectedCaseIds.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function onCaseSelectionChange(selection: EvalCase[]) {
  selectedCaseIds.value = selection.map((r) => r.id).filter(Boolean)
}

async function refreshMeta() {
  try {
    kbOptions.value = (await listKnowledgeBases()) || []
    const c = await countPendingDislikes()
    pendingDislikes.value = c?.count || 0
  } catch {
    /* ignore */
  }
}

function search() {
  query.page = 1
  void loadCases()
}

function onPageChange(payload: { page: number; pageSize: number }) {
  query.page = payload.page
  query.pageSize = payload.pageSize
  void loadCases()
}

watch(datasetId, () => {
  query.page = 1
  void loadCases()
})

function openCreateDataset() {
  datasetForm.name = ''
  datasetForm.knowledgeBaseId = kbOptions.value[0]?.id || ''
  datasetForm.description = ''
  datasetOpen.value = true
}

async function saveDataset() {
  if (!datasetForm.name.trim()) {
    ElMessage.warning('请填写评测集名称')
    return
  }
  datasetSaving.value = true
  try {
    const created = await createEvalDataset({
      name: datasetForm.name.trim(),
      knowledgeBaseId: datasetForm.knowledgeBaseId || undefined,
      description: datasetForm.description.trim() || undefined,
      sourceType: 'MANUAL',
    })
    ElMessage.success('已创建')
    datasetOpen.value = false
    await loadDatasets(created.id)
    await loadCases()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    datasetSaving.value = false
  }
}

async function removeDataset() {
  if (!datasetId.value || !currentDataset.value) return
  try {
    await ElMessageBox.confirm(
      `确认删除评测集「${currentDataset.value.name}」及其全部用例？`,
      '删除评测集',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteEvalDataset(datasetId.value)
    ElMessage.success('已删除')
    datasetId.value = ''
    await loadDatasets()
    await loadCases()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function openCreateCase() {
  if (!datasetId.value) {
    ElMessage.warning('请先创建或选择评测集')
    return
  }
  caseMode.value = 'create'
  editingCaseId.value = ''
  caseForm.question = ''
  caseForm.expectedAnswer = ''
  caseForm.includeInEval = 1
  caseForm.remark = ''
  caseOpen.value = true
}

function openEditCase(row: EvalCase) {
  caseMode.value = 'edit'
  editingCaseId.value = row.id
  caseForm.question = row.question
  caseForm.expectedAnswer = row.expectedAnswer || ''
  caseForm.includeInEval = row.includeInEval ?? 1
  caseForm.remark = row.remark || ''
  caseOpen.value = true
}

async function saveCase() {
  if (!caseForm.question.trim()) {
    ElMessage.warning('请填写问题')
    return
  }
  caseSaving.value = true
  try {
    const payload = {
      question: caseForm.question.trim(),
      expectedAnswer: caseForm.expectedAnswer.trim() || undefined,
      includeInEval: caseForm.includeInEval,
      remark: caseForm.remark.trim() || undefined,
      sourceType: 'MANUAL',
    }
    if (caseMode.value === 'create') {
      await createEvalCase(datasetId.value, payload)
      ElMessage.success('已录入')
    } else {
      await updateEvalCase(editingCaseId.value, payload)
      ElMessage.success('已保存')
    }
    caseOpen.value = false
    await loadDatasets(datasetId.value)
    await loadCases()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    caseSaving.value = false
  }
}

async function removeCase(row: EvalCase) {
  try {
    await ElMessageBox.confirm('确认删除该用例？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteEvalCase(row.id)
    ElMessage.success('已删除')
    await loadDatasets(datasetId.value)
    await loadCases()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function removeCasesBatch() {
  if (!ensureJwt()) return
  if (!selectedCaseIds.value.length) {
    ElMessage.warning('请先勾选要删除的用例')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedCaseIds.value.length} 条用例？`,
      '批量删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  batchDeletingCases.value = true
  try {
    const res = await batchDeleteEvalCases(selectedCaseIds.value)
    ElMessage.success(`已删除 ${res?.deleted ?? selectedCaseIds.value.length} 条`)
    await loadDatasets(datasetId.value)
    await loadCases()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  } finally {
    batchDeletingCases.value = false
  }
}

async function openGenerate() {
  if (!datasetId.value) {
    ElMessage.warning('请先选择评测集')
    return
  }
  genForm.knowledgeBaseId =
    currentDataset.value?.knowledgeBaseId || kbOptions.value[0]?.id || ''
  genForm.documentId = ''
  genForm.count = 5
  genOpen.value = true
  await loadDocs()
}

async function loadDocs() {
  if (!genForm.knowledgeBaseId) {
    docOptions.value = []
    return
  }
  docLoading.value = true
  try {
    const pageSize = 100
    const page = await listDocuments({
      knowledgeBaseId: genForm.knowledgeBaseId,
      page: 1,
      pageSize,
    })
    docOptions.value = page?.records || []
    if ((page?.total ?? 0) > pageSize) {
      ElMessage.warning(`文档下拉仅展示前 ${pageSize} 条（共 ${page.total}）`)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载文档失败')
  } finally {
    docLoading.value = false
  }
}

watch(
  () => genForm.knowledgeBaseId,
  () => {
    genForm.documentId = ''
    void loadDocs()
  },
)

async function startGenerate() {
  if (!genForm.documentId) {
    ElMessage.warning('请选择文档')
    return
  }
  genSaving.value = true
  try {
    const res = await generateEvalCasesFromDoc(datasetId.value, {
      knowledgeBaseId: genForm.knowledgeBaseId || undefined,
      documentId: genForm.documentId,
      count: genForm.count,
    })
    ElMessage.success(`已生成 ${res?.created ?? 0} 条用例`)
    genOpen.value = false
    await loadDatasets(datasetId.value)
    await loadCases()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '生成失败')
  } finally {
    genSaving.value = false
  }
}

async function importDislikes() {
  if (!datasetId.value) {
    ElMessage.warning('请先选择评测集')
    return
  }
  try {
    await ElMessageBox.confirm(
      '会把所有待沉淀的点踩问题转成用例，标准答案需要你手工补充，继续吗？',
      '沉淀确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const res = await importEvalCasesFromDislikes(datasetId.value)
    ElMessage.success(res?.message || `已导入 ${res?.created ?? 0} 条`)
    await refreshMeta()
    await loadDatasets(datasetId.value)
    await loadCases()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导入失败')
  }
}

function openSource(row: EvalCase) {
  sourceCase.value = row
  sourceOpen.value = true
}

onMounted(async () => {
  try {
    await refreshMeta()
    await loadDatasets()
    await loadCases()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '页面加载失败')
  }
})
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>评测集管理</h2>
        <p>维护问答评测用例：手工录入、文档大模型生成、点踩沉淀；用于检验应用配置是否满足业务。</p>
      </div>
    </header>

    <div class="panel-body">
      <div class="dataset-bar">
        <el-select v-model="datasetId" filterable placeholder="选择评测集" style="width: 320px">
          <el-option
            v-for="d in datasets"
            :key="d.id"
            :label="`${d.name}（${d.caseCount ?? 0}条用例）`"
            :value="d.id"
          />
        </el-select>
        <el-button type="primary" round @click="openCreateDataset">新建评测集</el-button>
        <el-button type="danger" plain round :disabled="!datasetId" @click="removeDataset">
          删除评测集
        </el-button>
      </div>

      <div class="toolbar">
        <div class="entry-btns">
          <el-button type="primary" plain round @click="openCreateCase">手工录入</el-button>
          <el-button type="success" plain round @click="openGenerate">从文档自动生成</el-button>
          <el-button round @click="importDislikes">
            从点踩沉淀{{ pendingDislikes ? `（${pendingDislikes}）` : '' }}
          </el-button>
          <el-button
            type="danger"
            plain
            round
            :loading="batchDeletingCases"
            :disabled="!selectedCaseIds.length"
            @click="removeCasesBatch"
          >
            批量删除用例
          </el-button>
        </div>
        <div class="filters">
          <el-select v-model="query.sourceType" clearable placeholder="按来源筛选" style="width: 140px">
            <el-option label="手工录入" value="MANUAL" />
            <el-option label="文档生成" value="FROM_DOC" />
            <el-option label="点踩沉淀" value="FROM_DISLIKE" />
          </el-select>
          <el-input
            v-model="query.question"
            clearable
            placeholder="按问题搜索"
            style="width: 220px"
            @keyup.enter="search"
          />
          <el-button round @click="search">查询</el-button>
        </div>
      </div>

      <el-table
        :data="cases"
        v-loading="loading"
        stripe
        empty-text="暂无用例"
        @selection-change="onCaseSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
        <el-table-column label="标准答案" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.expectedAnswer || '—' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" round effect="light" :type="sourceTagType(row.sourceType)">
              {{ sourceLabel(row.sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参与评测" width="90" align="center">
          <template #default="{ row }">
            <span :class="row.includeInEval === 1 ? 'yes' : 'no'">
              {{ row.includeInEval === 1 ? '是' : '否' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="success"
              size="small"
              round
              :disabled="!row.sourceContent && row.sourceType !== 'FROM_DOC'"
              @click="openSource(row)"
            >
              看原文
            </el-button>
            <el-button type="primary" size="small" round @click="openEditCase(row)">编辑</el-button>
            <el-button type="danger" size="small" round @click="removeCase(row)">删除</el-button>
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

    <AppModal v-model="datasetOpen" title="新建评测集" variant="form" width="560px">
      <div class="app-modal-body">
        <div class="app-modal-step form-grid">
          <label class="req">评测集名称</label>
          <el-input v-model="datasetForm.name" placeholder="例如：产品手册库-基线评测集" />
          <label>关联知识库</label>
          <el-select
            v-model="datasetForm.knowledgeBaseId"
            filterable
            clearable
            teleported
            popper-class="app-modal-select-popper"
            placeholder="这个评测集针对哪个知识库"
            style="width: 100%"
          >
            <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <label>说明</label>
          <el-input v-model="datasetForm.description" type="textarea" :rows="3" />
        </div>
      </div>
      <template #footer>
        <el-button round @click="datasetOpen = false">取消</el-button>
        <el-button type="primary" round :loading="datasetSaving" @click="saveDataset">确定</el-button>
      </template>
    </AppModal>

    <AppModal v-model="caseOpen" title="评测用例" variant="form" width="640px">
      <div class="app-modal-body">
        <div class="app-modal-step form-grid">
          <label class="req">问题</label>
          <el-input
            v-model="caseForm.question"
            type="textarea"
            :rows="3"
            placeholder="用户可能会这样问"
          />
          <label>标准答案</label>
          <el-input
            v-model="caseForm.expectedAnswer"
            type="textarea"
            :rows="3"
            placeholder="这个问题正确的答案是什么"
          />
          <label>参与评测</label>
          <el-radio-group v-model="caseForm.includeInEval">
            <el-radio :value="1">是</el-radio>
            <el-radio :value="0">否</el-radio>
          </el-radio-group>
          <label>备注</label>
          <el-input v-model="caseForm.remark" />
        </div>
      </div>
      <template #footer>
        <el-button round @click="caseOpen = false">取消</el-button>
        <el-button type="primary" round :loading="caseSaving" @click="saveCase">确定</el-button>
      </template>
    </AppModal>

    <AppModal v-model="genOpen" title="从文档自动生成用例" variant="form" width="560px">
      <div class="app-modal-body">
        <div class="app-modal-step form-grid">
          <label class="req">知识库</label>
          <el-select
            v-model="genForm.knowledgeBaseId"
            filterable
            teleported
            popper-class="app-modal-select-popper"
            placeholder="请选择知识库"
            style="width: 100%"
          >
            <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <label class="req">文档</label>
          <el-select
            v-model="genForm.documentId"
            filterable
            :loading="docLoading"
            teleported
            popper-class="app-modal-select-popper"
            placeholder="模型会读这篇文章的片段生成问答对"
            style="width: 100%"
          >
            <el-option
              v-for="d in docOptions"
              :key="d.id"
              :label="d.title || d.fileName || d.id"
              :value="d.id"
            />
          </el-select>
          <label>生成条数</label>
          <el-input-number v-model="genForm.count" :min="1" :max="20" />
        </div>
      </div>
      <template #footer>
        <el-button round @click="genOpen = false">取消</el-button>
        <el-button type="primary" round :loading="genSaving" @click="startGenerate">开始生成</el-button>
      </template>
    </AppModal>

    <AppModal v-model="sourceOpen" title="核对标准答案" variant="detail" width="720px">
      <div class="app-modal-body" v-if="sourceCase">
        <div class="source-block">
          <label>问题</label>
          <p>{{ sourceCase.question }}</p>
          <label>标准答案</label>
          <p>{{ sourceCase.expectedAnswer || '—' }}</p>
          <label>来源片段原文（模型就是照着这段出的题，对不上说明这条标准答案不能用）</label>
          <p class="meta">{{ sourceCase.sourceLabel || '—' }}</p>
          <pre class="source-text">{{ sourceCase.sourceContent || '暂无原文快照' }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button round @click="sourceOpen = false">关闭</el-button>
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
.dataset-bar,
.toolbar,
.entry-btns,
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.toolbar {
  justify-content: space-between;
}
.yes {
  color: #67c23a;
  font-weight: 600;
}
.no {
  color: var(--ink-muted);
}
.form-grid {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
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
.source-block label {
  display: block;
  margin: 10px 0 6px;
  font-size: 13px;
  color: var(--ink-soft);
}
.source-block p {
  margin: 0;
  line-height: 1.6;
}
.source-block .meta {
  color: var(--ink-muted);
  font-size: 12px;
  margin-bottom: 8px;
}
.source-text {
  margin: 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  max-height: 360px;
  overflow: auto;
}
</style>
