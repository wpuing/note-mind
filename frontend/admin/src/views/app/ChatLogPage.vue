<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminToken } from '@/api/ai'
import {
  fetchChatLogStats,
  getChatSessionMessages,
  pageChatFeedback,
  pageChatSessions,
  updateChatFeedbackSettled,
  type ChatFeedbackItem,
  type ChatFeedbackStat,
  type ChatMessage,
  type ChatSessionLog,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const tab = ref<'logs' | 'feedback'>('logs')
const loading = ref(false)
const stats = ref<ChatFeedbackStat>({ likeCount: 0, dislikeCount: 0, satisfactionRate: 0 })

const logQuery = reactive({ title: '', page: 1, pageSize: 10 })
const logRows = ref<ChatSessionLog[]>([])
const logTotal = ref(0)

const fbQuery = reactive({
  feedback: '' as '' | 'LIKE' | 'DISLIKE',
  settled: '' as '' | '0' | '1',
  page: 1,
  pageSize: 10,
})
const fbRows = ref<ChatFeedbackItem[]>([])
const fbTotal = ref(0)

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailSession = ref<ChatSessionLog | null>(null)
const detailMessages = ref<ChatMessage[]>([])

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

function sourceLabel(s?: string) {
  if (s === 'USER') return '用户端'
  return '管理端'
}

function feedbackLabel(f?: string) {
  if (f === 'LIKE') return '点赞'
  if (f === 'DISLIKE') return '点踩'
  return f || '—'
}

function truncate(text?: string, n = 48) {
  if (!text) return '—'
  const one = text.replace(/\s+/g, ' ').trim()
  return one.length > n ? `${one.slice(0, n)}…` : one
}

function sourceText(row: Record<string, unknown>) {
  const v = row.content ?? row.text ?? row.content_preview
  return v != null ? String(v) : '—'
}

async function loadStats() {
  try {
    stats.value = (await fetchChatLogStats()) || stats.value
  } catch {
    /* ignore */
  }
}

async function loadLogs() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pageChatSessions({
      title: logQuery.title || undefined,
      page: logQuery.page,
      pageSize: logQuery.pageSize,
    })
    logRows.value = page?.records || []
    logTotal.value = page?.total || 0
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadFeedback() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pageChatFeedback({
      feedback: fbQuery.feedback || undefined,
      settled: fbQuery.settled === '' ? undefined : Number(fbQuery.settled),
      page: fbQuery.page,
      pageSize: fbQuery.pageSize,
    })
    fbRows.value = page?.records || []
    fbTotal.value = page?.total || 0
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function searchLogs() {
  logQuery.page = 1
  void loadLogs()
}

function searchFeedback() {
  fbQuery.page = 1
  void loadFeedback()
}

function onTabChange(name: string | number) {
  if (name === 'feedback') void loadFeedback()
  else void loadLogs()
}

function onLogPage(payload: { page: number; pageSize: number }) {
  logQuery.page = payload.page
  logQuery.pageSize = payload.pageSize
  void loadLogs()
}

function onFbPage(payload: { page: number; pageSize: number }) {
  fbQuery.page = payload.page
  fbQuery.pageSize = payload.pageSize
  void loadFeedback()
}

async function openDetail(row: ChatSessionLog) {
  detailSession.value = row
  detailOpen.value = true
  detailLoading.value = true
  detailMessages.value = []
  try {
    detailMessages.value = (await getChatSessionMessages(row.id)) || []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载详情失败')
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

async function toggleSettled(row: ChatFeedbackItem) {
  const next = row.settled === 1 ? 0 : 1
  try {
    await updateChatFeedbackSettled(row.id, next)
    row.settled = next
    ElMessage.success(next === 1 ? '已标记沉淀' : '已取消沉淀')
    await loadStats()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  }
}

onMounted(async () => {
  await loadStats()
  await loadLogs()
})
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>对话日志与反馈</h2>
        <p>查看管理端问答会话与用户赞踩反馈，便于复盘与沉淀。</p>
      </div>
      <div class="stat-bar">
        <span>点赞 <b>{{ stats.likeCount }}</b></span>
        <span>点踩 <b>{{ stats.dislikeCount }}</b></span>
        <span>满意度 <b>{{ stats.satisfactionRate }}%</b></span>
      </div>
    </header>

    <div class="panel-body">
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane label="对话日志" name="logs">
          <div class="toolbar">
            <el-input
              v-model="logQuery.title"
              clearable
              placeholder="按会话标题搜索"
              style="width: 280px"
              @keyup.enter="searchLogs"
            />
            <el-button type="primary" round @click="searchLogs">查询</el-button>
          </div>

          <el-table :data="logRows" v-loading="loading" stripe empty-text="暂无会话">
            <el-table-column prop="id" label="会话ID" min-width="120" show-overflow-tooltip />
            <el-table-column prop="appName" label="所属应用" min-width="140" show-overflow-tooltip />
            <el-table-column label="会话标题" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.title || '—' }}</template>
            </el-table-column>
            <el-table-column prop="messageCount" label="消息数" width="80" align="center" />
            <el-table-column label="来源" width="90" align="center">
              <template #default="{ row }">{{ sourceLabel(row.clientSource) }}</template>
            </el-table-column>
            <el-table-column label="点赞" width="70" align="center">
              <template #default="{ row }">
                {{ row.likeCount ? row.likeCount : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="点踩" width="70" align="center">
              <template #default="{ row }">
                {{ row.dislikeCount ? row.dislikeCount : '—' }}
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="170" show-overflow-tooltip />
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" round @click="openDetail(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>

          <AppPagination
            :total="logTotal"
            :page="logQuery.page"
            :page-size="logQuery.pageSize"
            @change="onLogPage"
          />
        </el-tab-pane>

        <el-tab-pane label="用户反馈" name="feedback">
          <div class="toolbar">
            <el-select v-model="fbQuery.feedback" clearable placeholder="反馈类型" style="width: 140px">
              <el-option label="点赞" value="LIKE" />
              <el-option label="点踩" value="DISLIKE" />
            </el-select>
            <el-select v-model="fbQuery.settled" clearable placeholder="是否已沉淀" style="width: 140px">
              <el-option label="是" value="1" />
              <el-option label="否" value="0" />
            </el-select>
            <el-button type="primary" round @click="searchFeedback">查询</el-button>
          </div>

          <el-table :data="fbRows" v-loading="loading" stripe empty-text="暂无反馈">
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  round
                  effect="light"
                  :type="row.feedback === 'LIKE' ? 'success' : 'danger'"
                >
                  {{ feedbackLabel(row.feedback) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="问题" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.question || '—' }}</template>
            </el-table-column>
            <el-table-column label="回答" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ truncate(row.answer, 80) }}</template>
            </el-table-column>
            <el-table-column label="反馈原因" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.feedbackReason || '—' }}</template>
            </el-table-column>
            <el-table-column label="已沉淀" width="100" align="center">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  round
                  effect="plain"
                  :type="row.settled === 1 ? 'success' : 'info'"
                  class="settled-tag"
                  @click="toggleSettled(row)"
                >
                  {{ row.settled === 1 ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="170" show-overflow-tooltip />
          </el-table>

          <AppPagination
            :total="fbTotal"
            :page="fbQuery.page"
            :page-size="fbQuery.pageSize"
            @change="onFbPage"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <AppModal v-model="detailOpen" title="对话详情" variant="detail" width="720px">
      <div class="app-modal-body" v-loading="detailLoading">
        <p class="detail-meta" v-if="detailSession">
          会话 {{ detailSession.id }} · {{ detailSession.appName || '—' }}
        </p>
        <div class="chat-detail">
          <div
            v-for="m in detailMessages"
            :key="m.id"
            class="turn"
            :class="(m.role || '').toLowerCase()"
          >
            <div v-if="(m.role || '').toUpperCase() === 'USER'" class="bubble user">
              {{ m.content }}
            </div>
            <div v-else class="assistant-wrap">
              <div class="bubble assistant">
                <pre>{{ m.content }}</pre>
              </div>
              <details v-if="m.sources && m.sources.length" class="sources">
                <summary>引用来源（{{ m.sources.length }} 条）</summary>
                <ul>
                  <li v-for="(s, i) in m.sources" :key="i">
                    <b>#{{ i + 1 }}</b>
                    {{ sourceText(s) }}
                  </li>
                </ul>
              </details>
            </div>
          </div>
          <div v-if="!detailMessages.length" class="empty">暂无消息</div>
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
}
.stat-bar {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--ink-soft);
  white-space: nowrap;
}
.stat-bar b {
  color: var(--ink);
  margin-left: 4px;
}
.panel-body {
  padding: 8px 20px 24px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.settled-tag {
  cursor: pointer;
}
.detail-meta {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--ink-muted);
}
.chat-detail {
  display: grid;
  gap: 14px;
  max-height: 60vh;
  overflow: auto;
}
.turn.user {
  display: flex;
  justify-content: flex-end;
}
.bubble {
  max-width: 86%;
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble.user {
  background: #dbeafe;
  color: #1e3a5f;
}
.bubble.assistant {
  background: #f5f7fa;
  border: 1px solid var(--line);
}
.bubble.assistant pre {
  margin: 0;
  font-family: inherit;
  white-space: pre-wrap;
}
.assistant-wrap {
  display: grid;
  gap: 8px;
  max-width: 92%;
}
.sources {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px 12px;
  background: #fafafa;
  font-size: 13px;
}
.sources summary {
  cursor: pointer;
  color: var(--ink-soft);
}
.sources ul {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}
.empty {
  text-align: center;
  color: var(--ink-muted);
  padding: 24px;
}
</style>
