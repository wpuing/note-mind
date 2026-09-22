<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteRecallBenchRuns,
  compareRecallBench,
  deleteRecallBenchRun,
  getRecallBenchRun,
  listDocuments,
  listKnowledgeBases,
  listRetrievalStrategies,
  pageRecallBenchRuns,
  type KnowledgeBase,
  type KnowledgeDocument,
  type KnowledgeRetrievalTestResult,
  type RecallBenchRun,
  type RetrievalStrategy,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const loadingMeta = ref(false)
const loadingDocs = ref(false)
const comparing = ref(false)
const loadingHistory = ref(false)
const bases = ref<KnowledgeBase[]>([])
const strategies = ref<RetrievalStrategy[]>([])
const documents = ref<KnowledgeDocument[]>([])

const knowledgeBaseId = ref('')
const retrievalStrategyIds = ref<string[]>([])
const documentIds = ref<string[]>([])
const question = ref('')

const history = ref<RecallBenchRun[]>([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historyPageSize = ref(10)
const selectedHistoryIds = ref<string[]>([])
const batchDeletingHistory = ref(false)

const currentRun = ref<RecallBenchRun | null>(null)
const detailOpen = ref(false)
const detailLoading = ref(false)
const activeStrategyTab = ref('0')

function ensureJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT')
    return false
  }
  return true
}

function kbLabel(b: KnowledgeBase) {
  return `${b.name}（片段 ${b.segmentCount ?? 0}）`
}

function docLabel(d: KnowledgeDocument) {
  return `${d.title || d.fileName || d.id}（片段 ${d.segmentCount ?? 0}）`
}

async function loadDocs(kbId: string) {
  documents.value = []
  documentIds.value = []
  if (!kbId) return
  loadingDocs.value = true
  try {
    const pageSize = 200
    const page = await listDocuments({ knowledgeBaseId: kbId, page: 1, pageSize })
    documents.value = page?.records || []
    if ((page?.total ?? 0) > pageSize) {
      ElMessage.warning(`文档下拉仅展示前 ${pageSize} 条（共 ${page.total}）`)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载文档失败')
  } finally {
    loadingDocs.value = false
  }
}

async function loadMeta() {
  if (!ensureJwt()) return
  loadingMeta.value = true
  try {
    const [kbList, rsList] = await Promise.all([listKnowledgeBases(), listRetrievalStrategies()])
    bases.value = Array.isArray(kbList) ? kbList : []
    strategies.value = Array.isArray(rsList) ? rsList : []
    if (!knowledgeBaseId.value && bases.value[0]) {
      knowledgeBaseId.value = bases.value[0].id
    }
    if (knowledgeBaseId.value) await loadDocs(knowledgeBaseId.value)
    // 默认勾选基线 + 混合（若存在）
    if (!retrievalStrategyIds.value.length) {
      const prefer = ['rs_baseline_vector', 'rs_hybrid', 'rs_full']
      retrievalStrategyIds.value = prefer.filter((id) => strategies.value.some((s) => s.id === id)).slice(0, 3)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载选项失败')
  } finally {
    loadingMeta.value = false
  }
}

async function loadHistory() {
  if (!ensureJwt()) return
  loadingHistory.value = true
  try {
    const page = await pageRecallBenchRuns({
      knowledgeBaseId: knowledgeBaseId.value || undefined,
      page: historyPage.value,
      pageSize: historyPageSize.value,
    })
    history.value = page?.records || []
    historyTotal.value = page?.total || 0
    selectedHistoryIds.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载历史失败')
  } finally {
    loadingHistory.value = false
  }
}

function onStrategyChange(ids: string[]) {
  if (ids.length > 4) {
    ElMessage.warning('最多选择 4 套策略并排对比')
    retrievalStrategyIds.value = ids.slice(0, 4)
  }
}

async function runCompare() {
  if (!ensureJwt()) return
  if (!knowledgeBaseId.value) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!retrievalStrategyIds.value.length) {
    ElMessage.warning('请选择对比策略')
    return
  }
  if (retrievalStrategyIds.value.length > 4) {
    ElMessage.warning('最多选择 4 套策略')
    return
  }
  if (!question.value.trim()) {
    ElMessage.warning('请输入检索问题')
    return
  }
  comparing.value = true
  try {
    const run = await compareRecallBench({
      question: question.value.trim(),
      knowledgeBaseId: knowledgeBaseId.value,
      retrievalStrategyIds: [...retrievalStrategyIds.value],
      documentIds: documentIds.value.length ? [...documentIds.value] : undefined,
    })
    currentRun.value = run
    activeStrategyTab.value = '0'
    detailOpen.value = true
    historyPage.value = 1
    await loadHistory()
    ElMessage.success('对比完成')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '对比失败')
  } finally {
    comparing.value = false
  }
}

async function openHistory(row: RecallBenchRun) {
  detailOpen.value = true
  detailLoading.value = true
  activeStrategyTab.value = '0'
  currentRun.value = null
  try {
    currentRun.value = await getRecallBenchRun(row.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载详情失败')
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

async function removeHistory(row: RecallBenchRun) {
  try {
    await ElMessageBox.confirm('确认删除该调试记录？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteRecallBenchRun(row.id)
    ElMessage.success('已删除')
    await loadHistory()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function onHistorySelectionChange(selection: RecallBenchRun[]) {
  selectedHistoryIds.value = selection.map((r) => r.id).filter(Boolean)
}

async function removeHistoryBatch() {
  if (!ensureJwt()) return
  if (!selectedHistoryIds.value.length) {
    ElMessage.warning('请先勾选要删除的记录')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedHistoryIds.value.length} 条调试记录？`,
      '批量删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  batchDeletingHistory.value = true
  try {
    const res = await batchDeleteRecallBenchRuns(selectedHistoryIds.value)
    ElMessage.success(`已删除 ${res?.deleted ?? selectedHistoryIds.value.length} 条`)
    await loadHistory()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  } finally {
    batchDeletingHistory.value = false
  }
}

function onHistoryPage(payload: { page: number; pageSize: number }) {
  historyPage.value = payload.page
  historyPageSize.value = payload.pageSize
  void loadHistory()
}

const activeResult = computed<KnowledgeRetrievalTestResult | null>(() => {
  const list = currentRun.value?.results || []
  const idx = Number(activeStrategyTab.value) || 0
  return list[idx] || null
})

const hits = computed(() => {
  const r = activeResult.value
  if (!r) return []
  return (r.sources || r.segments || []) as Record<string, unknown>[]
})

const stages = computed(() => (activeResult.value?.stages || []) as Record<string, unknown>[])

function stageName(s: Record<string, unknown>) {
  return String(s.name || '—')
}
function stageCount(s: Record<string, unknown>) {
  return String(s.count_text ?? s.countText ?? '—')
}
function stageMs(s: Record<string, unknown>) {
  const v = s.elapsed_ms ?? s.elapsedMs
  return v == null ? '—' : String(v)
}
function stageThresholdLabel(s: Record<string, unknown>) {
  return String(s.threshold_label || s.thresholdLabel || '')
}
function hitScore(item: Record<string, unknown>) {
  const v = item.score
  if (v == null || v === '') return '—'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toFixed(6)
}
function hitPreview(item: Record<string, unknown>) {
  return String(item.content_preview || item.content || '').slice(0, 400)
}

watch(knowledgeBaseId, async (id, prev) => {
  if (id && id !== prev) {
    await loadDocs(id)
    historyPage.value = 1
    await loadHistory()
  }
})

onMounted(async () => {
  await loadMeta()
  await loadHistory()
})
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>召回调试台</h2>
        <p>最多勾选 4 套检索策略并排对比；可看各策略阶段耗时与命中片段，历史记录可回看。</p>
      </div>
    </header>

    <div class="panel-body">
      <el-form class="bench-form" label-width="96px" @submit.prevent>
        <el-form-item label="知识库" required>
          <el-select
            v-model="knowledgeBaseId"
            filterable
            :loading="loadingMeta"
            placeholder="请选择要检索的知识库"
            style="width: 100%"
          >
            <el-option v-for="b in bases" :key="b.id" :label="kbLabel(b)" :value="b.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="对比策略" required>
          <el-select
            v-model="retrievalStrategyIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="最多选择 4 套策略并排对比"
            style="width: 100%"
            @change="onStrategyChange"
          >
            <el-option
              v-for="s in strategies"
              :key="s.id"
              :label="`${s.name}${s.isDefault === 1 ? '（默认）' : ''}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="限定文档">
          <el-select
            v-model="documentIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :loading="loadingDocs"
            :disabled="!knowledgeBaseId"
            placeholder="不选表示在整个知识库里检索"
            style="width: 100%"
          >
            <el-option v-for="d in documents" :key="d.id" :label="docLabel(d)" :value="d.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="检索问题" required>
          <el-input
            v-model="question"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="输入一个用户可能会问的问题"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" round :loading="comparing" @click="runCompare">开始对比</el-button>
        </el-form-item>
      </el-form>

      <div class="history-block">
        <div class="history-head">
          <h3>历史调试记录</h3>
          <el-button
            type="danger"
            plain
            round
            size="small"
            :loading="batchDeletingHistory"
            :disabled="!selectedHistoryIds.length"
            @click="removeHistoryBatch"
          >
            批量删除
          </el-button>
        </div>
        <el-table
          v-loading="loadingHistory"
          :data="history"
          stripe
          border
          empty-text="暂无数据"
          @selection-change="onHistorySelectionChange"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column prop="question" label="问题" min-width="280" show-overflow-tooltip />
          <el-table-column label="对比策略" width="100" align="center">
            <template #default="{ row }">{{ row.strategyCount ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="耗时 (ms)" width="110" align="right">
            <template #default="{ row }">{{ row.elapsedMs ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="170" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openHistory(row)">查看</el-button>
              <el-button type="danger" link @click="removeHistory(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <AppPagination
          :page="historyPage"
          :page-size="historyPageSize"
          :total="historyTotal"
          @change="onHistoryPage"
        />
      </div>
    </div>

    <AppModal
      v-model="detailOpen"
      :title="currentRun?.question ? `对比结果：${currentRun.question.slice(0, 40)}` : '对比结果'"
      variant="detail"
      width="1080px"
    >
      <div v-loading="detailLoading" class="detail">
        <template v-if="currentRun">
          <p class="detail-meta">
            知识库：{{ currentRun.knowledgeBaseName || '—' }}
            · 策略 {{ currentRun.strategyCount ?? 0 }} 套
            · 总耗时 {{ currentRun.elapsedMs ?? '—' }} ms
          </p>

          <el-tabs v-model="activeStrategyTab" type="card" class="strategy-tabs">
            <el-tab-pane
              v-for="(r, idx) in currentRun.results || []"
              :key="r.strategyId || idx"
              :name="String(idx)"
              :label="r.strategyName || `策略 ${idx + 1}`"
            />
          </el-tabs>

          <div v-if="activeResult" class="strategy-panel">
            <div class="strategy-head">
              <h3>{{ activeResult.strategyName || '—' }}</h3>
              <p>
                召回 {{ activeResult.hitCount ?? hits.length }} 条 · 耗时
                {{ activeResult.elapsedMs ?? '—' }} ms
                <span v-if="activeResult.scoreScaleLabel">
                  · {{ activeResult.scoreScaleLabel }}
                </span>
              </p>
            </div>

            <el-table :data="stages" size="small" border stripe empty-text="无阶段明细">
              <el-table-column label="阶段" min-width="220">
                <template #default="{ row }">
                  <span>{{ stageName(row) }}</span>
                  <el-tag
                    v-if="stageThresholdLabel(row)"
                    size="small"
                    type="info"
                    effect="plain"
                    class="thr"
                  >
                    {{ stageThresholdLabel(row) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="条数" width="110" align="center">
                <template #default="{ row }">{{ stageCount(row) }}</template>
              </el-table-column>
              <el-table-column label="ms" width="90" align="right">
                <template #default="{ row }">{{ stageMs(row) }}</template>
              </el-table-column>
            </el-table>

            <el-table
              class="hit-table"
              :data="hits"
              size="small"
              border
              stripe
              empty-text="暂无数据"
              max-height="360"
            >
              <el-table-column type="index" label="#" width="56" />
              <el-table-column label="分数" width="120">
                <template #default="{ row }">{{ hitScore(row) }}</template>
              </el-table-column>
              <el-table-column label="片段" min-width="360" show-overflow-tooltip>
                <template #default="{ row }">{{ hitPreview(row) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button round @click="detailOpen = false">关闭</el-button>
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
}
.bench-form {
  max-width: 860px;
  margin-bottom: 28px;
}
.history-block h3 {
  margin: 0;
  font-size: 16px;
}
.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.detail-meta {
  margin: 0 0 12px;
  color: var(--ink-muted);
  font-size: 13px;
}
.strategy-tabs {
  margin-bottom: 12px;
}
.strategy-head h3 {
  margin: 0 0 4px;
  font-size: 18px;
}
.strategy-head p {
  margin: 0 0 12px;
  color: var(--ink-muted);
  font-size: 13px;
}
.thr {
  margin-left: 8px;
}
.hit-table {
  margin-top: 14px;
}
</style>
