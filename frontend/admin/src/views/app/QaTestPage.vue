<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  chatAppStream,
  createChatSession,
  feedbackChatMessage,
  getAgentRun,
  listChatMessages,
  listChatSessions,
  listQaApps,
  type AgentRun,
  type QaApp,
} from '@/api/http'
import { AppModal } from '@/components/common'

type UiMsg = {
  id: string
  role: 'user' | 'assistant'
  content: string
  statusText?: string
  sources?: Array<Record<string, unknown>>
  sourcesOpen?: boolean
  messageId?: string
  feedback?: string
  agentic?: boolean
  conclusionLabel?: string
  stepCount?: number
  retrievalRounds?: number
  rewriteRounds?: number
  agentRunId?: string
  streaming?: boolean
  processMeta?: {
    intentLevel?: string
    intentConfidence?: number
    intentReason?: string
    pipeline?: string
    cacheHit?: boolean
    cacheSimilarity?: number
    cacheMode?: string
    routerModel?: string
    generatorModel?: string
  }
}

const route = useRoute()
const apps = ref<QaApp[]>([])
const appId = ref('')
const sessionId = ref('')
const input = ref('')
const sending = ref(false)
let streamAbort: AbortController | null = null

onUnmounted(() => {
  streamAbort?.abort()
  streamAbort = null
})
const messages = ref<UiMsg[]>([])
const listRef = ref<HTMLElement | null>(null)

const timelineOpen = ref(false)
const timelineLoading = ref(false)
const timelineRun = ref<AgentRun | null>(null)

const currentApp = computed(() => apps.value.find((a) => a.id === appId.value))

function ensureJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT')
    return false
  }
  return true
}

function appLabel(app: QaApp) {
  return app.enableAgentic === 1 ? `${app.name} (Agentic)` : app.name
}

function sourceText(row: Record<string, unknown>) {
  const v = row.content ?? row.text ?? row.content_preview
  return v != null ? String(v) : '—'
}

function stepDotClass(node?: string) {
  if (node === 'retrieve') return 'dot-green'
  if (node === 'grade' || node === 'check') return 'dot-orange'
  return 'dot-blue'
}

async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

async function loadApps() {
  if (!ensureJwt()) return
  try {
    apps.value = (await listQaApps()) || []
    const qApp = typeof route.query.appId === 'string' ? route.query.appId : ''
    if (qApp && apps.value.some((a) => a.id === qApp)) {
      appId.value = qApp
    } else if (!appId.value && apps.value.length) {
      appId.value = apps.value[0].id
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载应用失败')
  }
}

async function newSession() {
  if (!ensureJwt()) return
  if (!appId.value) {
    ElMessage.warning('请选择应用')
    return
  }
  try {
    const s = await createChatSession(appId.value)
    sessionId.value = s.id
    messages.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '新建会话失败')
  }
}

/** 切应用：优先复用最近会话，避免 watch 自动狂建空会话 */
async function bindAppSession(id: string) {
  if (!ensureJwt() || !id) return
  try {
    const sessions = (await listChatSessions(id)) || []
    if (sessions.length) {
      sessionId.value = sessions[0].id
      const msgs = (await listChatMessages(sessionId.value)) || []
      messages.value = msgs.map((m) => ({
        id: m.id,
        role: m.role === 'USER' ? 'user' : 'assistant',
        content: m.content || '',
        messageId: m.id,
        feedback: m.feedback,
        sources: m.sources,
        agentRunId: m.agentRunId,
      }))
      return
    }
    await newSession()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载会话失败')
  }
}

watch(appId, (id) => {
  if (!id) return
  void bindAppSession(id)
})

async function send() {
  const q = input.value.trim()
  if (!q || sending.value) return
  if (!ensureJwt()) return
  if (!appId.value) {
    ElMessage.warning('请选择应用')
    return
  }
  if (!sessionId.value) {
    await newSession()
  }

  sending.value = true
  input.value = ''
  const userMsg: UiMsg = {
    id: `u_${Date.now()}`,
    role: 'user',
    content: q,
  }
  const botMsg: UiMsg = reactive({
    id: `a_${Date.now()}`,
    role: 'assistant',
    content: '',
    statusText: '处理中…',
    sources: [],
    sourcesOpen: false,
    streaming: true,
    agentic: currentApp.value?.enableAgentic === 1,
  })
  messages.value.push(userMsg, botMsg)
  await scrollBottom()

  streamAbort?.abort()
  streamAbort = new AbortController()
  try {
    await chatAppStream(
      { appId: appId.value, sessionId: sessionId.value || undefined, question: q },
      {
        onSession: (p) => {
          if (p?.sessionId) sessionId.value = p.sessionId
        },
        onStatus: (text) => {
          botMsg.statusText = text
          void scrollBottom()
        },
        onMeta: (payload) => {
          botMsg.processMeta = {
            intentLevel: payload.intentLevel as string | undefined,
            intentConfidence: payload.intentConfidence as number | undefined,
            intentReason: payload.intentReason as string | undefined,
            pipeline: payload.pipeline as string | undefined,
            cacheHit: Boolean(payload.cacheHit),
            cacheSimilarity: payload.cacheSimilarity as number | undefined,
            cacheMode: payload.cacheMode as string | undefined,
            routerModel: payload.routerModel as string | undefined,
            generatorModel: payload.generatorModel as string | undefined,
          }
          void scrollBottom()
        },
        onSources: (sources) => {
          botMsg.sources = sources || []
          botMsg.sourcesOpen = false
          void scrollBottom()
        },
        onDelta: (text) => {
          botMsg.content += text
          if (botMsg.content) botMsg.statusText = ''
          void scrollBottom()
        },
        onFinal: (payload) => {
          botMsg.messageId = String(payload.messageId || '')
          botMsg.content = String(payload.answer || botMsg.content || '')
          botMsg.sources = (payload.sources as Array<Record<string, unknown>>) || botMsg.sources
          botMsg.conclusionLabel = payload.conclusionLabel as string | undefined
          botMsg.stepCount = payload.stepCount as number | undefined
          botMsg.retrievalRounds = payload.retrievalRounds as number | undefined
          botMsg.rewriteRounds = payload.rewriteRounds as number | undefined
          botMsg.agentRunId = payload.agentRunId as string | undefined
          botMsg.agentic = Number(payload.enableAgentic) === 1
          botMsg.statusText = ''
          botMsg.streaming = false
          if (payload.sessionId) sessionId.value = String(payload.sessionId)
        },
        onError: (message) => {
          botMsg.statusText = ''
          botMsg.content = botMsg.content || `出错了：${message}`
          botMsg.streaming = false
        },
      },
      { signal: streamAbort.signal },
    )
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') return
    botMsg.statusText = ''
    botMsg.content = e instanceof Error ? e.message : '发送失败'
    botMsg.streaming = false
  } finally {
    sending.value = false
    botMsg.streaming = false
    await scrollBottom()
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void send()
  }
}

async function setFeedback(msg: UiMsg, feedback: 'LIKE' | 'DISLIKE') {
  if (!msg.messageId) return
  try {
    const next = msg.feedback === feedback ? '' : feedback
    await feedbackChatMessage(msg.messageId, next as 'LIKE' | 'DISLIKE' | '')
    msg.feedback = next
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '反馈失败')
  }
}

async function openTimeline(msg: UiMsg) {
  if (!msg.agentRunId) return
  timelineOpen.value = true
  timelineLoading.value = true
  timelineRun.value = null
  try {
    timelineRun.value = await getAgentRun(msg.agentRunId)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载时间线失败')
    timelineOpen.value = false
  } finally {
    timelineLoading.value = false
  }
}

onMounted(() => {
  void loadApps()
})
</script>

<template>
  <section class="chat-page">
    <header class="chat-toolbar">
      <el-select v-model="appId" filterable placeholder="选择应用" style="width: 260px">
        <el-option v-for="a in apps" :key="a.id" :label="appLabel(a)" :value="a.id" />
      </el-select>
      <el-button round @click="newSession">新建会话</el-button>
      <span class="session-id" v-if="sessionId">当前会话: {{ sessionId }}</span>
      <router-link class="manage-link" to="/app/manage">应用管理</router-link>
    </header>

    <div class="chat-list" ref="listRef">
      <div v-if="!messages.length" class="empty">
        选择应用后输入问题。Agentic 应用会展示检索 / 判断相关 / 生成等过程状态，必要时自动改写并多次检索。
      </div>

      <div v-for="m in messages" :key="m.id" class="turn" :class="m.role">
        <div v-if="m.role === 'user'" class="bubble user">{{ m.content }}</div>

        <div v-else class="assistant-block">
          <div v-if="m.statusText" class="status">
            <span class="pulse" />
            {{ m.statusText }}
          </div>

          <div v-if="m.processMeta" class="process-panel">
            <el-tag size="small" effect="plain">意图 {{ m.processMeta.intentLevel || '—' }}</el-tag>
            <el-tag size="small" :type="m.processMeta.cacheHit ? 'success' : 'info'" effect="plain">
              缓存 {{ m.processMeta.cacheHit ? `命中 ${m.processMeta.cacheSimilarity?.toFixed?.(3) ?? ''}` : '未命中' }}
            </el-tag>
            <el-tag size="small" effect="plain">管线 {{ m.processMeta.pipeline || '—' }}</el-tag>
            <span class="muted">{{ m.processMeta.intentReason }}</span>
            <span class="muted">router={{ m.processMeta.routerModel }} / gen={{ m.processMeta.generatorModel }}</span>
          </div>

          <div v-if="m.content" class="bubble assistant">
            <pre>{{ m.content }}</pre>
            <div class="feedback" v-if="!m.streaming && m.messageId">
              <button
                type="button"
                :class="{ on: m.feedback === 'LIKE' }"
                @click="setFeedback(m, 'LIKE')"
              >
                👍 有用
              </button>
              <button
                type="button"
                :class="{ on: m.feedback === 'DISLIKE' }"
                @click="setFeedback(m, 'DISLIKE')"
              >
                👎 没帮助
              </button>
            </div>
          </div>

          <div
            v-if="m.agentic && !m.streaming && (m.stepCount || m.conclusionLabel)"
            class="agent-meta"
          >
            <el-tag size="small" round effect="light" type="success">
              {{ m.conclusionLabel || '完成' }}
            </el-tag>
            <span>共 {{ m.stepCount ?? 0 }} 步</span>
            <span>检索 {{ m.retrievalRounds ?? 0 }} 次</span>
            <span>改写 {{ m.rewriteRounds ?? 0 }} 次</span>
            <button
              v-if="m.agentRunId"
              type="button"
              class="link"
              @click="openTimeline(m)"
            >
              查看时间线
            </button>
          </div>

          <details
            v-if="m.sources && m.sources.length"
            class="sources"
          >
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
    </div>

    <footer class="composer">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="输入问题，回车发送"
        :disabled="sending"
        @keydown="onKeydown"
      />
      <el-button type="primary" round :icon="Promotion" :loading="sending" @click="send">
        发送
      </el-button>
    </footer>

    <AppModal v-model="timelineOpen" title="执行时间线" variant="form" width="720px">
      <div class="app-modal-body" v-loading="timelineLoading">
        <div class="app-modal-step" v-if="timelineRun">
          <div class="timeline-head">
            <el-tag
              size="small"
              round
              effect="light"
              :type="timelineRun.status === 'SUCCESS' ? 'success' : 'danger'"
            >
              {{ timelineRun.status === 'SUCCESS' ? '已完成' : '运行失败' }}
            </el-tag>
            <span>共 {{ timelineRun.stepCount ?? 0 }} 步</span>
            <span>检索 {{ timelineRun.retrievalRounds ?? 0 }} 次</span>
            <span>改写 {{ timelineRun.rewriteRounds ?? 0 }} 次</span>
            <span>耗时 {{ timelineRun.totalLatencyMs ?? '—' }} ms</span>
          </div>
          <ul class="timeline">
            <li v-for="(s, idx) in timelineRun.steps || []" :key="s.id || idx">
              <span class="dot" :class="stepDotClass(s.nodeName)" />
              <div class="step-body">
                <h4>{{ s.title || s.nodeName }}</h4>
                <p v-if="s.inputJson" class="io"><b>入参</b> {{ s.inputJson }}</p>
                <p v-if="s.outputJson" class="io"><b>返回</b> {{ s.outputJson }}</p>
                <p class="meta">第 {{ s.stepIndex }} 步 · {{ s.nodeName }}</p>
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
.chat-page {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: calc(100vh - var(--header-height) - 48px);
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--surface);
  box-shadow: var(--shadow);
  overflow: hidden;
}
.chat-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--line);
  background: #fafbfc;
}
.session-id {
  color: var(--ink-muted);
  font-size: 13px;
}
.manage-link {
  margin-left: auto;
  color: var(--matcha-700, #3d6b2e);
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
}
.chat-list {
  overflow: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #fff;
}
.empty {
  margin: auto;
  color: var(--ink-muted);
  font-size: 14px;
  text-align: center;
  max-width: 420px;
  line-height: 1.6;
}
.turn.user {
  display: flex;
  justify-content: flex-end;
}
.bubble {
  max-width: min(720px, 86%);
  padding: 12px 14px;
  border-radius: 14px;
  line-height: 1.65;
  font-size: 14px;
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
.assistant-block {
  display: grid;
  gap: 8px;
  max-width: min(760px, 92%);
}
.status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #409eff;
  font-size: 13px;
}
.process-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  max-width: min(760px, 92%);
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  font-size: 12px;
}
.process-panel .muted {
  color: #64748b;
}
.pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
  animation: blink 1s infinite;
}
@keyframes blink {
  50% {
    opacity: 0.35;
  }
}
.feedback {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
.feedback button {
  border: none;
  background: transparent;
  color: var(--ink-muted);
  cursor: pointer;
  font-size: 12px;
  padding: 0;
}
.feedback button.on {
  color: #409eff;
  font-weight: 600;
}
.agent-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  font-size: 12px;
  color: var(--ink-muted);
}
.agent-meta .link {
  border: none;
  background: none;
  color: #409eff;
  cursor: pointer;
  padding: 0;
  font-size: 12px;
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
  list-style: none;
}
.sources summary::-webkit-details-marker {
  display: none;
}
.sources summary::after {
  content: ' ›';
  color: #999;
}
.sources ul {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}
.sources li {
  color: var(--ink);
  line-height: 1.5;
  word-break: break-word;
}
.composer {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: end;
  padding: 14px 18px;
  border-top: 1px solid var(--line);
  background: #fafbfc;
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
.meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--ink-muted);
}
</style>
