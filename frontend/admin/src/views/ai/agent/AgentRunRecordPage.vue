<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteAgentRuns,
  getAgentRun,
  pageAgentRuns,
  type AgentRun,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const loading = ref(false)
const rows = ref<AgentRun[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
const batchDeleting = ref(false)
const query = reactive({
  status: '' as string,
  page: 1,
  pageSize: 10,
})

const timelineOpen = ref(false)
const timelineLoading = ref(false)
const detail = ref<AgentRun | null>(null)

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

function truncate(text?: string, n = 48) {
  if (!text) return '—'
  const one = text.replace(/\s+/g, ' ').trim()
  return one.length > n ? `${one.slice(0, n)}…` : one
}

function statusLabel(s?: string) {
  if (s === 'SUCCESS') return '已完成'
  if (s === 'FAILED') return '运行失败'
  if (s === 'RUNNING') return '运行中'
  return s || '—'
}

function runTypeLabel(t?: string) {
  if (!t || t === 'AGENTIC_QA') return 'Agentic问答'
  return t
}

function stepDotClass(node?: string) {
  if (node === 'retrieve') return 'dot-green'
  if (node === 'grade' || node === 'check') return 'dot-orange'
  return 'dot-blue'
}

async function load() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pageAgentRuns({
      status: query.status || undefined,
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

function onSelectionChange(selection: AgentRun[]) {
  selectedIds.value = selection.map((r) => r.id).filter(Boolean)
}

async function removeBatch() {
  if (!ensureJavaJwt()) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选要删除的运行记录')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedIds.value.length} 条运行记录？`,
      '批量删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const res = await batchDeleteAgentRuns(selectedIds.value)
    ElMessage.success(`已删除 ${res?.deleted ?? selectedIds.value.length} 条`)
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

function resetFilters() {
  query.status = ''
  query.page = 1
  void load()
}

function onPageChange(payload: { page: number; pageSize: number }) {
  query.page = payload.page
  query.pageSize = payload.pageSize
  void load()
}

async function openTimeline(row: AgentRun) {
  if (!ensureJavaJwt()) return
  timelineOpen.value = true
  timelineLoading.value = true
  detail.value = null
  try {
    detail.value = await getAgentRun(row.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载时间线失败')
    timelineOpen.value = false
  } finally {
    timelineLoading.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>Agent 运行记录</h2>
        <p>
          查看 Agentic 问答历史；可从
          <router-link class="inline-link" to="/ai/agent">Agent 执行</router-link>
          发起新测试。
        </p>
      </div>
      <el-button round :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="panel-body">
      <div class="toolbar">
        <div class="filters">
          <el-select v-model="query.status" clearable placeholder="运行状态" style="width: 160px">
            <el-option label="已完成" value="SUCCESS" />
            <el-option label="运行失败" value="FAILED" />
            <el-option label="运行中" value="RUNNING" />
          </el-select>
          <el-button type="primary" round @click="search">查询</el-button>
          <el-button round @click="resetFilters">重置</el-button>
        </div>
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

      <el-table
        :data="rows"
        v-loading="loading"
        stripe
        empty-text="暂无运行记录"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="运行ID" min-width="130" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ runTypeLabel(row.runType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              round
              effect="light"
              :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="stepCount" label="步数" width="70" align="center" />
        <el-table-column prop="retrievalRounds" label="检索" width="70" align="center" />
        <el-table-column prop="rewriteRounds" label="改写" width="70" align="center" />
        <el-table-column label="最终结论" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ truncate(row.finalAnswer, 60) }}</template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.status === 'FAILED' ? row.errorMessage || '—' : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="100" align="center">
          <template #default="{ row }">{{ row.totalLatencyMs ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" show-overflow-tooltip />
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" round @click="openTimeline(row)">时间线</el-button>
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

    <AppModal v-model="timelineOpen" title="执行时间线" variant="form" width="720px">
      <div class="app-modal-body" v-loading="timelineLoading">
        <div class="app-modal-step" v-if="detail">
          <div class="timeline-head">
            <el-tag
              size="small"
              round
              effect="light"
              :type="detail.status === 'SUCCESS' ? 'success' : 'danger'"
            >
              {{ statusLabel(detail.status) }}
            </el-tag>
            <span>共 {{ detail.stepCount ?? 0 }} 步</span>
            <span>检索 {{ detail.retrievalRounds ?? 0 }} 次</span>
            <span>改写 {{ detail.rewriteRounds ?? 0 }} 次</span>
            <span>耗时 {{ detail.totalLatencyMs ?? '—' }} ms</span>
          </div>
          <ul class="timeline">
            <li v-for="(s, idx) in detail.steps || []" :key="s.id || idx">
              <span class="dot" :class="stepDotClass(s.nodeName)" />
              <div class="step-body">
                <h4>{{ s.title || s.nodeName }}</h4>
                <p v-if="s.inputJson" class="io"><b>入参</b> {{ s.inputJson }}</p>
                <p v-if="s.outputJson" class="io"><b>返回</b> {{ s.outputJson }}</p>
                <p class="meta">第 {{ s.stepIndex }} 步 · {{ s.nodeName }} · {{ s.latencyMs ?? 0 }} ms</p>
              </div>
            </li>
          </ul>
        </div>
      </div>
      <template #footer>
        <el-button round @click="timelineOpen = false">关闭</el-button>
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
}
.inline-link {
  color: var(--matcha-700, #3d6b2e);
  text-decoration: none;
  font-weight: 600;
}
.panel-body {
  padding: 16px 20px 24px;
  display: grid;
  gap: 12px;
}
.toolbar,
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.toolbar {
  justify-content: space-between;
}
.timeline-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  font-size: 13px;
}
.timeline {
  list-style: none;
  margin: 0;
  padding: 0 0 0 8px;
  border-left: 2px solid #e5e7eb;
  display: grid;
  gap: 16px;
}
.timeline li {
  position: relative;
  padding-left: 18px;
}
.dot {
  position: absolute;
  left: -7px;
  top: 6px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #409eff;
}
.dot-green {
  background: #67c23a;
}
.dot-orange {
  background: #e6a23c;
}
.step-body h4 {
  margin: 0 0 6px;
  font-size: 14px;
}
.io {
  margin: 0 0 4px;
  font-size: 12px;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.io b {
  font-family: inherit;
  color: var(--ink-muted);
  margin-right: 6px;
}
.meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--ink-muted);
}
</style>
