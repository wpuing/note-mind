<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Upload,
  Search,
  ChatDotRound,
  Cpu,
  CircleCheck,
  CircleClose,
  Warning,
  Collection,
  Document,
  QuestionFilled,
  Timer,
} from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsType } from 'echarts/core'
import { getAdminToken } from '@/api/ai'
import {
  fetchDashboardOverview,
  type DashboardOverview,
} from '@/api/http'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
])

const COLORS = ['#4b743d', '#7fad6c', '#3d5d33', '#a3c794', '#5f914e', '#c7dec0', '#344b2c']

type Health = 'ok' | 'down' | 'checking'

const router = useRouter()
const aiHealth = ref<Health>('checking')
const javaHealth = ref<Health>('checking')
const loading = ref(false)
const days = ref(14)
const overview = ref<DashboardOverview | null>(null)

const usageChartRef = ref<HTMLDivElement | null>(null)
const kbChartRef = ref<HTMLDivElement | null>(null)
const modelChartRef = ref<HTMLDivElement | null>(null)
const modelTypeChartRef = ref<HTMLDivElement | null>(null)
let usageChart: EChartsType | null = null
let kbChart: EChartsType | null = null
let modelChart: EChartsType | null = null
let modelTypeChart: EChartsType | null = null

async function probeAi() {
  try {
    const r = await fetch('/health')
    aiHealth.value = r.ok ? 'ok' : 'down'
  } catch {
    aiHealth.value = 'down'
  }
}

async function probeJava() {
  try {
    const r = await fetch('/api/v1/ping')
    javaHealth.value = r.ok ? 'ok' : 'down'
  } catch {
    javaHealth.value = 'down'
  }
}

function ensureJwt() {
  const token = getAdminToken() || ''
  return !!(token && !token.startsWith('local-'))
}

async function loadOverview() {
  if (!ensureJwt()) {
    overview.value = null
    return
  }
  loading.value = true
  try {
    overview.value = await fetchDashboardOverview(days.value)
    await nextTick()
    renderCharts()
  } catch (e) {
    overview.value = null
    console.warn('[dashboard] overview load failed', e)
  } finally {
    loading.value = false
  }
}

function disposeCharts() {
  usageChart?.dispose()
  kbChart?.dispose()
  modelChart?.dispose()
  modelTypeChart?.dispose()
  usageChart = null
  kbChart = null
  modelChart = null
  modelTypeChart = null
}

function renderCharts() {
  const data = overview.value
  if (!data) return

  if (usageChartRef.value) {
    usageChart ??= echarts.init(usageChartRef.value)
    const trend = data.usageTrend || []
    usageChart.setOption({
      color: COLORS,
      tooltip: { trigger: 'axis' },
      legend: { data: ['提问', '对话调用', 'Agent', '工具调用'], bottom: 0 },
      grid: { left: 40, right: 16, top: 24, bottom: 48 },
      xAxis: {
        type: 'category',
        data: trend.map((d) => d.date.slice(5)),
        axisLabel: { color: '#6e7b6a' },
      },
      yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#6e7b6a' }, splitLine: { lineStyle: { color: '#e3efe0' } } },
      series: [
        { name: '提问', type: 'line', smooth: true, data: trend.map((d) => d.questions) },
        { name: '对话调用', type: 'line', smooth: true, data: trend.map((d) => d.chatCalls) },
        { name: 'Agent', type: 'bar', data: trend.map((d) => d.agentRuns), barMaxWidth: 18 },
        { name: '工具调用', type: 'bar', data: trend.map((d) => d.toolCalls), barMaxWidth: 18 },
      ],
    })
  }

  if (kbChartRef.value) {
    kbChart ??= echarts.init(kbChartRef.value)
    const rows = [...(data.hotKnowledgeBases || [])].reverse()
    kbChart.setOption({
      color: ['#4b743d'],
      tooltip: { trigger: 'axis' },
      grid: { left: 100, right: 24, top: 16, bottom: 28 },
      xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#e3efe0' } } },
      yAxis: {
        type: 'category',
        data: rows.map((r) => (r.name.length > 12 ? `${r.name.slice(0, 12)}…` : r.name)),
        axisLabel: { color: '#4a5747', width: 90, overflow: 'truncate' },
      },
      series: [
        {
          type: 'bar',
          data: rows.map((r) => r.count),
          barMaxWidth: 22,
          label: { show: true, position: 'right', color: '#6e7b6a' },
        },
      ],
    })
  }

  if (modelChartRef.value) {
    modelChart ??= echarts.init(modelChartRef.value)
    const rows = data.modelCalls || []
    modelChart.setOption({
      color: COLORS,
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 12, top: 16, bottom: 64 },
      xAxis: {
        type: 'category',
        data: rows.map((r) => (r.name.length > 10 ? `${r.name.slice(0, 10)}…` : r.name)),
        axisLabel: { rotate: 28, color: '#6e7b6a', fontSize: 11 },
      },
      yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#e3efe0' } } },
      series: [{ type: 'bar', data: rows.map((r) => r.count), barMaxWidth: 28 }],
    })
  }

  if (modelTypeChartRef.value) {
    modelTypeChart ??= echarts.init(modelTypeChartRef.value)
    const rows = data.modelTypeCalls || []
    modelTypeChart.setOption({
      color: COLORS,
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['38%', '64%'],
          center: ['50%', '46%'],
          data: rows.map((r) => ({ name: r.name, value: r.count })),
          label: { formatter: '{b}\n{c}' },
        },
      ],
    })
  }
}

function onResize() {
  usageChart?.resize()
  kbChart?.resize()
  modelChart?.resize()
  modelTypeChart?.resize()
}

function goKb(id?: string) {
  if (!id) return
  void router.push({ path: '/knowledge/base' })
}

const readyActions = [
  {
    title: '1. 文件上传与切分向量化',
    desc: '上传 PDF/TXT，解析切分并写入 PGVector',
    path: '/knowledge/document',
    icon: Upload,
    tag: '可测',
  },
  {
    title: '2. 检索测试',
    desc: '混合检索 + 重排，查看命中片段与分数',
    path: '/knowledge/retrieval-test',
    icon: Search,
    tag: '可测',
  },
  {
    title: '3. 问答测试',
    desc: 'SSE 流式回答，底部展示引用溯源',
    path: '/app',
    icon: ChatDotRound,
    tag: '可测',
  },
  {
    title: '模型配置',
    desc: '查看 CHAT / EMBEDDING / RERANK 种子',
    path: '/ai/model',
    icon: Cpu,
    tag: '可测',
  },
]

watch(days, () => void loadOverview())

onMounted(() => {
  void probeAi()
  void probeJava()
  void loadOverview()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  disposeCharts()
})
</script>

<template>
  <section class="dash" v-loading="loading">
    <header class="hero">
      <div>
        <p class="eyebrow">NoteMind Admin · MVP</p>
        <h1>AI 企业知识库管理工作台</h1>
        <p class="desc">
          汇总近 {{ days }} 天真实使用：热门知识库、高频提问、使用时段与模型调用。下方入口可继续联调验收。
        </p>
      </div>
      <div class="hero-right">
        <el-select v-model="days" style="width: 120px" size="small">
          <el-option :value="7" label="近 7 天" />
          <el-option :value="14" label="近 14 天" />
          <el-option :value="30" label="近 30 天" />
        </el-select>
        <div class="health">
          <div class="h-item" :data-s="aiHealth">
            <el-icon>
              <CircleCheck v-if="aiHealth === 'ok'" />
              <CircleClose v-else-if="aiHealth === 'down'" />
              <Warning v-else />
            </el-icon>
            <span>AI :8000 {{ aiHealth === 'ok' ? '正常' : aiHealth === 'down' ? '不可达' : '检测中' }}</span>
          </div>
          <div class="h-item" :data-s="javaHealth">
            <el-icon>
              <CircleCheck v-if="javaHealth === 'ok'" />
              <CircleClose v-else-if="javaHealth === 'down'" />
              <Warning v-else />
            </el-icon>
            <span>Gateway :8080 {{ javaHealth === 'ok' ? '正常' : javaHealth === 'down' ? '不可达' : '检测中' }}</span>
          </div>
        </div>
      </div>
    </header>

    <div v-if="overview" class="kpi-grid">
      <div class="kpi">
        <el-icon><Collection /></el-icon>
        <div>
          <strong>{{ overview.summary.knowledgeBaseCount }}</strong>
          <span>知识库</span>
        </div>
      </div>
      <div class="kpi">
        <el-icon><Document /></el-icon>
        <div>
          <strong>{{ overview.summary.documentCount }}</strong>
          <span>文档</span>
        </div>
      </div>
      <div class="kpi">
        <el-icon><QuestionFilled /></el-icon>
        <div>
          <strong>{{ overview.summary.questionCount }}</strong>
          <span>提问次数</span>
        </div>
      </div>
      <div class="kpi">
        <el-icon><ChatDotRound /></el-icon>
        <div>
          <strong>{{ overview.summary.chatSessionCount }}</strong>
          <span>新建会话</span>
        </div>
      </div>
      <div class="kpi">
        <el-icon><Cpu /></el-icon>
        <div>
          <strong>{{ overview.summary.agentRunCount }}</strong>
          <span>Agent 运行</span>
        </div>
      </div>
      <div class="kpi">
        <el-icon><Timer /></el-icon>
        <div>
          <strong>{{ overview.summary.toolCallCount }}</strong>
          <span>工具调用</span>
        </div>
      </div>
    </div>

    <div v-if="overview" class="charts-grid">
      <article class="panel-card wide">
        <header>
          <h3>使用时间趋势</h3>
          <p>按日统计提问、对话模型调用、Agent 与工具调用</p>
        </header>
        <div ref="usageChartRef" class="chart" role="img" aria-label="使用时间趋势图" />
      </article>

      <article class="panel-card">
        <header>
          <h3>热门知识库</h3>
          <p>问答 / Agent / 召回调试合计命中次数</p>
        </header>
        <div
          v-if="overview.hotKnowledgeBases?.length"
          ref="kbChartRef"
          class="chart"
          role="img"
          aria-label="热门知识库图"
        />
        <p v-else class="empty">暂无使用记录</p>
        <ul v-if="overview.hotKnowledgeBases?.length" class="kb-list">
          <li v-for="(kb, i) in overview.hotKnowledgeBases" :key="kb.id || i">
            <button type="button" class="linkish" @click="goKb(kb.id)">{{ kb.name }}</button>
            <span>{{ kb.count }} 次</span>
          </li>
        </ul>
      </article>

      <article class="panel-card">
        <header>
          <h3>高频提问</h3>
          <p>会话用户消息与 Agent 问题聚合</p>
        </header>
        <ol v-if="overview.hotQuestions?.length" class="q-list">
          <li v-for="(q, i) in overview.hotQuestions" :key="i">
            <div class="q-main">
              <span class="rank">{{ i + 1 }}</span>
              <span class="q-text" :title="q.question">{{ q.question }}</span>
            </div>
            <div class="q-meta">
              <b>{{ q.count }}</b> 次
              <span v-if="q.lastAskTime">· {{ q.lastAskTime }}</span>
            </div>
          </li>
        </ol>
        <p v-else class="empty">暂无提问数据</p>
      </article>

      <article class="panel-card">
        <header>
          <h3>模型调用频率</h3>
          <p>按配置名称统计（对话 + 检索关联的向量/重排）</p>
        </header>
        <div
          v-if="overview.modelCalls?.length"
          ref="modelChartRef"
          class="chart"
          role="img"
          aria-label="模型调用柱状图"
        />
        <p v-else class="empty">暂无模型调用</p>
      </article>

      <article class="panel-card">
        <header>
          <h3>模型类型占比</h3>
          <p>CHAT / EMBEDDING / RERANK</p>
        </header>
        <div
          v-if="overview.modelTypeCalls?.length"
          ref="modelTypeChartRef"
          class="chart"
          role="img"
          aria-label="模型类型占比图"
        />
        <p v-else class="empty">暂无类型数据</p>
      </article>
    </div>

    <p v-else-if="!loading" class="hint warn">
      统计需登录 JWT。请先登录后再刷新工作台。
    </p>

    <h2 class="sec-title">联调演示（请按顺序点）</h2>
    <div class="actions">
      <router-link
        v-for="item in readyActions"
        :key="item.path"
        :to="item.path"
        class="action"
      >
        <span class="tag">{{ item.tag }}</span>
        <el-icon :size="22"><component :is="item.icon" /></el-icon>
        <strong>{{ item.title }}</strong>
        <span>{{ item.desc }}</span>
      </router-link>
    </div>

    <p class="hint">
      口径：提问 = 用户消息 + Agent 问题；对话调用 ≈ 助手回复 + Agent 运行；向量/重排按
      <code>search_knowledge</code> 检索次数关联到当前启用的向量/重排配置。
    </p>
  </section>
</template>

<style scoped>
.dash {
  display: grid;
  gap: 16px;
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  padding: 28px 28px 24px;
  border-radius: 22px;
  border: 1px solid var(--line);
  background:
    linear-gradient(135deg, rgba(75, 116, 61, 0.92), rgba(61, 93, 51, 0.88) 45%, rgba(127, 173, 108, 0.75)),
    radial-gradient(600px 240px at 90% 10%, rgba(255, 255, 255, 0.2), transparent 60%);
  color: #f4faf1;
  box-shadow: var(--shadow);
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  opacity: 0.78;
}

.hero h1 {
  margin: 0 0 10px;
  font-size: clamp(26px, 4vw, 34px);
  font-weight: 700;
  letter-spacing: -0.03em;
}

.desc {
  margin: 0;
  max-width: 40rem;
  opacity: 0.9;
  font-size: 14px;
  line-height: 1.65;
}

.hero-right {
  display: grid;
  gap: 10px;
  align-content: start;
  justify-items: end;
}

.health {
  display: grid;
  gap: 8px;
  align-content: start;
}

.h-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 12px;
  white-space: nowrap;
}

.h-item[data-s='ok'] {
  box-shadow: inset 0 0 0 1px rgba(163, 199, 148, 0.55);
}
.h-item[data-s='down'] {
  background: rgba(180, 60, 60, 0.35);
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.kpi {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--shadow);
}

.kpi .el-icon {
  color: var(--matcha-600);
  font-size: 22px;
}

.kpi strong {
  display: block;
  font-size: 22px;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.kpi span {
  font-size: 12px;
  color: var(--ink-muted);
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.panel-card {
  display: grid;
  gap: 10px;
  padding: 16px 18px 14px;
  border-radius: 18px;
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--shadow);
  min-height: 280px;
}

.panel-card.wide {
  grid-column: 1 / -1;
}

.panel-card header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
}

.panel-card header p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--ink-muted);
}

.chart {
  width: 100%;
  height: 260px;
}

.panel-card.wide .chart {
  height: 300px;
}

.empty {
  margin: 40px 0;
  text-align: center;
  color: var(--ink-muted);
  font-size: 13px;
}

.kb-list,
.q-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.kb-list li,
.q-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  padding: 8px 0;
  border-top: 1px dashed var(--line);
  font-size: 13px;
}

.kb-list li:first-child,
.q-list li:first-child {
  border-top: none;
}

.linkish {
  border: 0;
  background: none;
  padding: 0;
  color: var(--matcha-700);
  cursor: pointer;
  text-align: left;
  font: inherit;
}

.linkish:hover {
  text-decoration: underline;
}

.q-main {
  display: flex;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.rank {
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: var(--matcha-100);
  color: var(--matcha-700);
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.q-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.q-meta {
  flex: 0 0 auto;
  color: var(--ink-muted);
  font-size: 12px;
  white-space: nowrap;
}

.q-meta b {
  color: var(--ink);
}

.sec-title {
  margin: 4px 0 0;
  font-size: 15px;
  font-weight: 650;
  color: var(--ink-soft);
}

.actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.action {
  position: relative;
  display: grid;
  gap: 8px;
  justify-items: start;
  text-align: left;
  text-decoration: none;
  padding: 18px 16px;
  border-radius: 18px;
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: var(--shadow);
  cursor: pointer;
  color: inherit;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.action:hover {
  transform: translateY(-2px);
  border-color: rgba(75, 116, 61, 0.35);
}

.action .el-icon {
  color: var(--matcha-600);
}

.action strong {
  font-size: 15px;
}

.action span:last-child {
  font-size: 12px;
  color: var(--ink-muted);
  line-height: 1.5;
}

.tag {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--matcha-100);
  color: var(--matcha-700);
  font-size: 11px;
  font-weight: 700;
}

.hint {
  margin: 0;
  font-size: 13px;
  color: var(--ink-muted);
  line-height: 1.6;
}

.hint.warn {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--matcha-50);
  color: var(--matcha-800);
}

.hint code {
  padding: 1px 6px;
  border-radius: 6px;
  background: var(--matcha-50);
  font-size: 12px;
}

@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .actions,
  .charts-grid {
    grid-template-columns: 1fr;
  }
  .panel-card.wide {
    grid-column: auto;
  }
}

@media (max-width: 640px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .actions {
    grid-template-columns: 1fr;
  }
  .hero-right {
    justify-items: start;
    width: 100%;
  }
}
</style>
