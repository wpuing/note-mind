<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteAgentToolCallLogs,
  pageAgentToolCallLogs,
  pageAgentTools,
  type AgentTool,
  type AgentToolCallLog,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const rows = ref<AgentToolCallLog[]>([])
const total = ref(0)
const toolOptions = ref<AgentTool[]>([])
const selectedIds = ref<string[]>([])
const batchDeleting = ref(false)

const query = reactive({
  toolCode: '' as string,
  status: '' as '' | 'OK' | 'FAILED',
  agentRunId: '',
  page: 1,
  pageSize: 10,
})

const detailOpen = ref(false)
const detail = ref<AgentToolCallLog | null>(null)

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

function applyRouteQuery() {
  const toolCode = typeof route.query.toolCode === 'string' ? route.query.toolCode : ''
  const status = typeof route.query.status === 'string' ? route.query.status : ''
  const agentRunId = typeof route.query.agentRunId === 'string' ? route.query.agentRunId : ''
  query.toolCode = toolCode
  query.status = status === 'OK' || status === 'FAILED' ? status : ''
  query.agentRunId = agentRunId
  query.page = 1
}

function syncRouteQuery() {
  const q: Record<string, string> = {}
  if (query.toolCode) q.toolCode = query.toolCode
  if (query.status) q.status = query.status
  if (query.agentRunId.trim()) q.agentRunId = query.agentRunId.trim()
  void router.replace({ path: '/ai/tool-log', query: q })
}

function isSuccess(status?: string) {
  return !status || status.toUpperCase() === 'OK'
}

function truncateJson(raw?: string, n = 56) {
  if (!raw) return '—'
  const one = raw.replace(/\s+/g, ' ').trim()
  return one.length > n ? `${one.slice(0, n)}…` : one
}

function pretty(raw?: string | null) {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

async function loadTools() {
  try {
    const page = await pageAgentTools({ page: 1, pageSize: 100 })
    toolOptions.value = page?.records || []
  } catch {
    toolOptions.value = []
  }
}

async function load() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pageAgentToolCallLogs({
      toolCode: query.toolCode || undefined,
      status: query.status || undefined,
      agentRunId: query.agentRunId.trim() || undefined,
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

function onSelectionChange(selection: AgentToolCallLog[]) {
  selectedIds.value = selection.map((r) => r.id).filter(Boolean)
}

async function removeBatch() {
  if (!ensureJavaJwt()) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选要删除的日志')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedIds.value.length} 条工具调用日志？`,
      '批量删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const res = await batchDeleteAgentToolCallLogs(selectedIds.value)
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
  syncRouteQuery()
  void load()
}

function resetFilters() {
  query.toolCode = ''
  query.status = ''
  query.agentRunId = ''
  query.page = 1
  syncRouteQuery()
  void load()
}

function onPageChange(payload: { page: number; pageSize: number }) {
  query.page = payload.page
  query.pageSize = payload.pageSize
  void load()
}

function openDetail(row: AgentToolCallLog) {
  detail.value = row
  detailOpen.value = true
}

onMounted(async () => {
  applyRouteQuery()
  await loadTools()
  void load()
})
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>工具调用日志</h2>
        <p>
          记录问答与 Agent 中的真实工具调用（当前为
          <code>search_knowledge</code>：Agentic / Agent 执行会带运行ID；直线问答检索也会落日志）。可从
          <router-link class="inline-link" to="/ai/tool">工具中心 · 调用统计</router-link>
          跳转筛选；无数据时请先跑
          <router-link class="inline-link" to="/ai/agent">Agent 执行</router-link>
          或问答测试。
        </p>
      </div>
      <el-button round :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="panel-body">
      <div class="toolbar">
        <div class="filters">
          <el-select
            v-model="query.toolCode"
            clearable
            filterable
            placeholder="请选择工具"
            style="width: 200px"
          >
            <el-option
              v-for="t in toolOptions"
              :key="t.code"
              :label="`${t.name}（${t.code}）`"
              :value="t.code"
            />
          </el-select>
          <el-select v-model="query.status" clearable placeholder="调用结果" style="width: 140px">
            <el-option label="成功" value="OK" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-input
            v-model="query.agentRunId"
            clearable
            placeholder="按运行ID筛选"
            style="width: 200px"
            @keyup.enter="search"
          />
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
        empty-text="暂无真实调用日志（请先运行 Agent / Agentic 问答）"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="日志ID" min-width="120" show-overflow-tooltip />
        <el-table-column label="运行ID" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.agentRunId || '—' }}</template>
        </el-table-column>
        <el-table-column label="工具" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.toolName || row.toolCode }}
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              round
              effect="light"
              :type="isSuccess(row.status) ? 'success' : 'danger'"
            >
              {{ isSuccess(row.status) ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入参" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ truncateJson(row.inputJson) }}</template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ isSuccess(row.status) ? '—' : row.errorMessage || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="100" align="center">
          <template #default="{ row }">
            {{ row.latencyMs == null ? '—' : row.latencyMs }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" round @click="openDetail(row)">详情</el-button>
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

    <AppModal v-model="detailOpen" title="调用日志详情" variant="form" width="720px">
      <div class="app-modal-body" v-if="detail">
        <div class="app-modal-step">
          <div class="detail-grid">
            <label>日志ID</label>
            <div>{{ detail.id }}</div>
            <label>运行ID</label>
            <div>{{ detail.agentRunId || '—' }}</div>
            <label>工具</label>
            <div>{{ detail.toolName || detail.toolCode }}（{{ detail.toolCode }}）</div>
            <label>结果</label>
            <div>
              <el-tag
                size="small"
                round
                effect="light"
                :type="isSuccess(detail.status) ? 'success' : 'danger'"
              >
                {{ isSuccess(detail.status) ? '成功' : '失败' }}
              </el-tag>
            </div>
            <label>耗时</label>
            <div>{{ detail.latencyMs == null ? '—' : `${detail.latencyMs} ms` }}</div>
            <label>时间</label>
            <div>{{ detail.createTime || '—' }}</div>
            <label>失败原因</label>
            <div>{{ detail.errorMessage || '—' }}</div>
            <label>入参</label>
            <pre class="json-block">{{ pretty(detail.inputJson) }}</pre>
            <label>出参</label>
            <pre class="json-block">{{ pretty(detail.outputJson) }}</pre>
          </div>
        </div>
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
  max-width: 640px;
}
.inline-link {
  color: var(--matcha-700, #3d6b2e);
  text-decoration: none;
  font-weight: 600;
}
.inline-link:hover {
  text-decoration: underline;
}
.panel-body {
  padding: 16px 20px 24px;
  display: grid;
  gap: 12px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.detail-grid {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px 10px;
  align-items: start;
}
.detail-grid label {
  font-size: 13px;
  color: var(--ink-soft);
  text-align: right;
  padding-top: 2px;
}
.json-block {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f3f5f2;
  border: 1px solid var(--line);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.55;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  max-height: 240px;
  overflow: auto;
}
</style>
