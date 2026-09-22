<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteEvalReports,
  deleteEvalReport,
  getEvalReport,
  listEvalDatasets,
  listRetrievalStrategies,
  pageEvalReports,
  startEvalBatch,
  type EvalDataset,
  type EvalReport,
  type EvalReportCase,
  type RetrievalStrategy,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const loading = ref(false)
const starting = ref(false)
const batchDeleting = ref(false)
const datasets = ref<EvalDataset[]>([])
const strategies = ref<RetrievalStrategy[]>([])
const reports = ref<EvalReport[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
const query = reactive({ page: 1, pageSize: 10 })
const form = reactive({ datasetId: '', retrievalStrategyId: '' })

const reportOpen = ref(false)
const reportLoading = ref(false)
const report = ref<EvalReport | null>(null)

let pollTimer: ReturnType<typeof setInterval> | null = null

const TH = {
  contextRecall: 0.7,
  contextPrecision: 0.6,
  faithfulness: 0.8,
  answerRelevancy: 0.7,
}

function ensureJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT')
    return false
  }
  return true
}

function statusLabel(s?: string) {
  if (s === 'RUNNING') return '评测中'
  if (s === 'COMPLETED') return '已完成'
  if (s === 'FAILED') return '失败'
  return s || '—'
}

function statusType(s?: string) {
  if (s === 'RUNNING') return 'warning'
  if (s === 'COMPLETED') return 'success'
  if (s === 'FAILED') return 'danger'
  return 'info'
}

function fmtScoreNice(v?: number | null) {
  if (v === undefined || v === null || Number.isNaN(Number(v))) return '—'
  const n = Number(v)
  if (Math.abs(n - Math.round(n)) < 1e-9) return String(Math.round(n))
  return n.toFixed(4).replace(/\.?0+$/, '')
}

function met(score: number | undefined | null, th: number) {
  return score != null && score >= th
}

const metricCards = computed(() => {
  const r = report.value
  if (!r) return []
  return [
    {
      key: 'contextRecall',
      title: '上下文召回',
      en: 'Context Recall',
      tag: '找资料',
      score: r.contextRecall,
      th: TH.contextRecall,
      desc: '标准答案里说的东西，检索找回来了吗',
    },
    {
      key: 'contextPrecision',
      title: '上下文精度',
      en: 'Context Precision',
      tag: '找资料',
      score: r.contextPrecision,
      th: TH.contextPrecision,
      desc: '找回来的资料里，有用的排在前面吗',
    },
    {
      key: 'faithfulness',
      title: '忠实度',
      en: 'Faithfulness',
      tag: '写答案',
      score: r.faithfulness,
      th: TH.faithfulness,
      desc: '答案里每句话，在资料里有出处吗（有没有编）',
    },
    {
      key: 'answerRelevancy',
      title: '答案相关性',
      en: 'Answer Relevancy',
      tag: '写答案',
      score: r.answerRelevancy,
      th: TH.answerRelevancy,
      desc: '答案是在回答这个问题吗（有没有跑题）',
    },
  ]
})

const allMet = computed(() =>
  metricCards.value.every((m) => met(m.score, m.th)),
)

async function loadMeta() {
  if (!ensureJwt()) return
  try {
    datasets.value = (await listEvalDatasets()) || []
    strategies.value = (await listRetrievalStrategies()) || []
    if (!form.datasetId && datasets.value.length) {
      form.datasetId = datasets.value[0].id
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载选项失败')
  }
}

async function loadReports() {
  if (!ensureJwt()) return
  loading.value = true
  try {
    const page = await pageEvalReports({ page: query.page, pageSize: query.pageSize })
    reports.value = page?.records || []
    total.value = page?.total || 0
    const running = reports.value.some((r) => r.status === 'RUNNING')
    if (running) startPoll()
    else stopPoll()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function startPoll() {
  if (pollTimer) return
  pollTimer = setInterval(() => {
    void loadReports()
  }, 3000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function onPageChange(payload: { page: number; pageSize: number }) {
  query.page = payload.page
  query.pageSize = payload.pageSize
  void loadReports()
}

async function onStart() {
  if (!form.datasetId) {
    ElMessage.warning('请选择评测集')
    return
  }
  starting.value = true
  try {
    await startEvalBatch({
      datasetId: form.datasetId,
      retrievalStrategyId: form.retrievalStrategyId || undefined,
    })
    ElMessage.success('已开始评测，稍后刷新查看结果')
    query.page = 1
    await loadReports()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '启动失败')
  } finally {
    starting.value = false
  }
}

async function openReport(row: EvalReport) {
  reportOpen.value = true
  reportLoading.value = true
  report.value = null
  try {
    report.value = await getEvalReport(row.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载报告失败')
    reportOpen.value = false
  } finally {
    reportLoading.value = false
  }
}

async function removeReport(row: EvalReport) {
  try {
    await ElMessageBox.confirm(`确认删除任务 ${row.taskNo ?? row.id}？`, '删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteEvalReport(row.id)
    ElMessage.success('已删除')
    await loadReports()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function onSelectionChange(rows: EvalReport[]) {
  selectedIds.value = rows.map((r) => r.id)
}

async function removeBatch() {
  if (!ensureJwt()) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选评测任务')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedIds.value.length} 条评测任务？`,
      '批量删除',
      { type: 'warning', confirmButtonText: '删除' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const res = await batchDeleteEvalReports(selectedIds.value)
    ElMessage.success(`已删除 ${res.deleted} 条`)
    selectedIds.value = []
    await loadReports()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

function caseScore(row: EvalReportCase, key: keyof EvalReportCase) {
  const v = row[key]
  return typeof v === 'number' ? fmtScoreNice(v) : '—'
}

onMounted(async () => {
  await loadMeta()
  await loadReports()
})

onUnmounted(() => stopPoll())
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>批量评测</h2>
        <p>选评测集与检索策略跑一轮，按上下文召回 / 精度 / 忠实度 / 答案相关性四维出报告。</p>
      </div>
      <div class="head-actions">
        <el-button
          type="danger"
          plain
          round
          :loading="batchDeleting"
          :disabled="!selectedIds.length"
          @click="removeBatch"
        >
          批量删除
        </el-button>
      </div>
    </header>

    <div class="panel-body">
      <div class="start-bar">
        <el-select v-model="form.datasetId" filterable placeholder="请选择评测集" style="width: 280px">
          <el-option
            v-for="d in datasets"
            :key="d.id"
            :label="`${d.name}（${d.caseCount ?? 0} 条用例）`"
            :value="d.id"
          />
        </el-select>
        <el-select
          v-model="form.retrievalStrategyId"
          clearable
          filterable
          placeholder="不选则使用默认策略"
          style="width: 320px"
        >
          <el-option v-for="s in strategies" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button type="primary" :loading="starting" @click="onStart">开始评测</el-button>
      </div>
      <p class="hint">
        一条用例要调六次模型，20 条用例一轮大约几十万输入 token。建议先用 5 条验证方向。
      </p>

      <el-table
        v-loading="loading"
        :data="reports"
        stripe
        border
        empty-text="暂无数据"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="任务ID" width="90">
          <template #default="{ row }">{{ row.taskNo ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="datasetName" label="评测集" min-width="140" show-overflow-tooltip />
        <el-table-column prop="strategyName" label="检索策略" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="90">
          <template #default="{ row }">
            {{ row.doneCount ?? 0 }} / {{ row.caseCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" min-width="120" show-overflow-tooltip />
        <el-table-column label="耗时 (ms)" width="110">
          <template #default="{ row }">{{ row.durationMs ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openReport(row)">看报告</el-button>
            <el-button type="danger" link @click="removeReport(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <AppPagination
        :page="query.page"
        :page-size="query.pageSize"
        :total="total"
        @change="onPageChange"
      />
    </div>

    <AppModal v-model="reportOpen" title="评测报告" width="1100px">
      <div v-loading="reportLoading" class="report">
        <template v-if="report">
          <div class="report-meta">
            <span>评测集：{{ report.datasetName || '—' }}</span>
            <span>策略：{{ report.strategyName || '—' }}</span>
            <span>用例：{{ report.caseCount ?? 0 }} 条</span>
            <span>
              综合得分：
              <strong class="score">{{ fmtScoreNice(report.overallScore) }}</strong>
            </span>
          </div>

          <div class="metric-grid">
            <div v-for="m in metricCards" :key="m.key" class="metric-card">
              <div class="metric-top">
                <div>
                  <div class="metric-title">{{ m.title }}</div>
                  <div class="metric-en">{{ m.en }}</div>
                </div>
                <el-tag size="small" type="info" effect="plain">{{ m.tag }}</el-tag>
              </div>
              <div class="metric-score" :class="{ ok: met(m.score, m.th) }">
                {{ fmtScoreNice(m.score) }}
              </div>
              <p class="metric-desc">{{ m.desc }}</p>
              <div class="metric-th" :class="{ ok: met(m.score, m.th) }">
                {{ met(m.score, m.th) ? '达标' : '未达标' }} (≥ {{ m.th }})
              </div>
            </div>
          </div>

          <div class="summary-bar" :class="{ ok: allMet }">
            {{ allMet ? '四项指标都在合理区间。' : '部分指标未达标，可对比不同检索策略后再优化。' }}
          </div>

          <el-table :data="report.cases || []" border stripe size="small" max-height="360">
            <el-table-column prop="question" label="问题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="expectedAnswer" label="标准答案" min-width="100" show-overflow-tooltip />
            <el-table-column prop="generatedAnswer" label="生成的答案" min-width="160" show-overflow-tooltip />
            <el-table-column label="召回" width="70">
              <template #default="{ row }">{{ caseScore(row, 'contextRecall') }}</template>
            </el-table-column>
            <el-table-column label="精度" width="70">
              <template #default="{ row }">{{ caseScore(row, 'contextPrecision') }}</template>
            </el-table-column>
            <el-table-column label="忠实度" width="70">
              <template #default="{ row }">{{ caseScore(row, 'faithfulness') }}</template>
            </el-table-column>
            <el-table-column label="相关性" width="80">
              <template #default="{ row }">{{ caseScore(row, 'answerRelevancy') }}</template>
            </el-table-column>
            <el-table-column label="均分" width="70">
              <template #default="{ row }">{{ caseScore(row, 'avgScore') }}</template>
            </el-table-column>
            <el-table-column prop="failReason" label="失败原因" min-width="100" show-overflow-tooltip />
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button round @click="reportOpen = false">关闭</el-button>
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
  align-items: flex-start;
}
.panel-body {
  padding: 16px 20px 24px;
  display: grid;
  gap: 12px;
}
.start-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: var(--ink-muted);
}
.report {
  min-height: 200px;
  display: grid;
  gap: 14px;
}
.report-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: var(--ink-soft);
}
.report-meta .score {
  color: #67c23a;
  font-size: 16px;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.metric-card {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px;
  background: #fafcfa;
}
.metric-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.metric-title {
  font-weight: 600;
  font-size: 14px;
}
.metric-en {
  font-size: 11px;
  color: var(--ink-muted);
}
.metric-score {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  color: #909399;
}
.metric-score.ok {
  color: #67c23a;
}
.metric-desc {
  margin: 8px 0;
  font-size: 12px;
  color: var(--ink-muted);
  line-height: 1.4;
  min-height: 34px;
}
.metric-th {
  font-size: 12px;
  color: #909399;
}
.metric-th.ok {
  color: #67c23a;
}
.summary-bar {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f4f4f5;
  color: #606266;
  font-size: 13px;
}
.summary-bar.ok {
  background: #f0f9eb;
  color: #67c23a;
}
@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
