<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsType } from 'echarts/core'
import { getAdminToken } from '@/api/ai'
import {
  listEvalDatasets,
  pageEvalReports,
  type EvalDataset,
  type EvalReport,
} from '@/api/http'

echarts.use([BarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const COLORS = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452']

const loading = ref(false)
const datasets = ref<EvalDataset[]>([])
const datasetId = ref('')
const reports = ref<EvalReport[]>([])
const selected = ref<EvalReport[]>([])
const comparing = ref(false)
const chartRef = ref<HTMLDivElement | null>(null)
let chart: EChartsType | null = null

const metricDefs = [
  { key: 'contextRecall' as const, label: '上下文召回' },
  { key: 'contextPrecision' as const, label: '上下文精度' },
  { key: 'faithfulness' as const, label: '忠实度' },
  { key: 'answerRelevancy' as const, label: '答案相关性' },
]

function ensureJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT')
    return false
  }
  return true
}

function fmtScore(v?: number | null) {
  if (v === undefined || v === null || Number.isNaN(Number(v))) return '—'
  const n = Number(v)
  if (Math.abs(n - Math.round(n)) < 1e-9) return String(Math.round(n))
  return n.toFixed(4).replace(/\.?0+$/, '')
}

const currentDatasetName = computed(
  () => datasets.value.find((d) => d.id === datasetId.value)?.name || reports.value[0]?.datasetName || '评测集',
)

const compareTitle = computed(
  () => `${currentDatasetName.value} · ${selected.value.length} 策略对比`,
)

const matrixRows = computed(() =>
  metricDefs.map((m) => ({
    label: m.label,
    values: selected.value.map((r) => fmtScore(r[m.key])),
  })),
)

async function loadDatasets() {
  if (!ensureJwt()) return
  datasets.value = (await listEvalDatasets()) || []
  if (!datasetId.value && datasets.value.length) {
    datasetId.value = datasets.value[0].id
  }
}

async function loadReports() {
  if (!ensureJwt() || !datasetId.value) {
    reports.value = []
    return
  }
  loading.value = true
  try {
    const page = await pageEvalReports({
      datasetId: datasetId.value,
      status: 'COMPLETED',
      page: 1,
      pageSize: 50,
    })
    reports.value = page?.records || []
    selected.value = []
    comparing.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: EvalReport[]) {
  selected.value = rows
}

function startCompare() {
  if (selected.value.length < 2) {
    ElMessage.warning('请在表格里勾选两份以上的报告')
    return
  }
  comparing.value = true
  void nextTick(() => renderChart())
}

function renderChart() {
  if (!chartRef.value || selected.value.length < 2) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  const categories = metricDefs.map((m) => m.label)
  const series = selected.value.map((r, i) => ({
    name: r.strategyName || r.id,
    type: 'bar' as const,
    barMaxWidth: 36,
    itemStyle: { color: COLORS[i % COLORS.length] },
    data: metricDefs.map((m) => Number(r[m.key] ?? 0)),
  }))
  chart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
      },
      legend: {
        bottom: 0,
        type: 'scroll',
      },
      grid: {
        left: 48,
        right: 24,
        top: 28,
        bottom: 56,
      },
      xAxis: {
        type: 'category',
        data: categories,
        axisTick: { alignWithLabel: true },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 1,
        splitNumber: 5,
      },
      series,
    },
    true,
  )
}

function onResize() {
  chart?.resize()
}

watch(datasetId, () => {
  void loadReports()
})

watch(comparing, (v) => {
  if (v) void nextTick(() => renderChart())
})

onMounted(async () => {
  await loadDatasets()
  await loadReports()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>策略对比</h2>
        <p>同一评测集下勾选多份已完成报告，对比不同检索策略在四维指标上的表现。</p>
      </div>
    </header>

    <div class="panel-body">
      <div class="toolbar">
        <el-select v-model="datasetId" filterable placeholder="请选择评测集" style="width: 280px">
          <el-option
            v-for="d in datasets"
            :key="d.id"
            :label="`${d.name}（${d.caseCount ?? 0}条）`"
            :value="d.id"
          />
        </el-select>
        <el-button type="primary" :disabled="selected.length < 2" @click="startCompare">
          对比选中的报告
        </el-button>
        <span class="hint">在表格里勾选两份以上的报告</span>
      </div>

      <el-table
        v-loading="loading"
        :data="reports"
        stripe
        border
        empty-text="暂无已完成的评测报告，请先到「批量评测」跑几轮"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="strategyName" label="策略" min-width="180" show-overflow-tooltip />
        <el-table-column label="启用的能力" min-width="240">
          <template #default="{ row }">
            <div class="tags">
              <el-tag
                v-for="t in row.capabilityTags || []"
                :key="t"
                size="small"
                type="primary"
                effect="plain"
              >
                {{ t }}
              </el-tag>
              <span v-if="!(row.capabilityTags && row.capabilityTags.length)" class="muted">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="用例" width="70">
          <template #default="{ row }">{{ row.caseCount ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="召回" width="80">
          <template #default="{ row }">{{ fmtScore(row.contextRecall) }}</template>
        </el-table-column>
        <el-table-column label="精度" width="80">
          <template #default="{ row }">{{ fmtScore(row.contextPrecision) }}</template>
        </el-table-column>
        <el-table-column label="忠实度" width="80">
          <template #default="{ row }">{{ fmtScore(row.faithfulness) }}</template>
        </el-table-column>
        <el-table-column label="相关性" width="90">
          <template #default="{ row }">{{ fmtScore(row.answerRelevancy) }}</template>
        </el-table-column>
        <el-table-column label="综合" width="80">
          <template #default="{ row }">{{ fmtScore(row.overallScore) }}</template>
        </el-table-column>
        <el-table-column label="单条耗时 (ms)" width="120">
          <template #default="{ row }">{{ row.avgLatencyMs ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>

      <div v-if="comparing && selected.length >= 2" class="compare-panel">
        <h3>{{ compareTitle }}</h3>
        <div ref="chartRef" class="chart" />
        <el-table :data="matrixRows" border stripe size="small" class="matrix">
          <el-table-column prop="label" label="指标" width="140" fixed />
          <el-table-column
            v-for="(r, i) in selected"
            :key="r.id"
            :label="r.strategyName || r.id"
            min-width="160"
          >
            <template #default="{ row }">{{ row.values[i] }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
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
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.hint {
  font-size: 12px;
  color: var(--ink-muted);
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.muted {
  color: var(--ink-muted);
}
.compare-panel {
  margin-top: 8px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  display: grid;
  gap: 12px;
}
.compare-panel h3 {
  margin: 0;
  font-size: 16px;
}
.chart {
  width: 100%;
  height: 360px;
}
.matrix {
  width: 100%;
}
</style>
