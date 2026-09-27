<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, Plus, Promotion } from '@element-plus/icons-vue'
import {
  chatAppStream,
  createChatSession,
  feedbackChatMessage,
  listChatMessages,
  listChatSessions,
  listQaApps,
  type ChatSession,
  type QaApp,
} from '@/api/http'
import { getWebToken } from '@/api/auth'

/** 页面消息气泡模型（用户 / 助手，含流式状态与赞踩）。 */
type UiMsg = {
  id: string
  role: 'user' | 'assistant'
  content: string
  statusText?: string
  sources?: Array<Record<string, unknown>>
  sourcesOpen?: boolean
  messageId?: string
  feedback?: string
  conclusionLabel?: string
  stepCount?: number
  retrievalRounds?: number
  rewriteRounds?: number
  streaming?: boolean
}

/** 可选问答应用列表。 */
const apps = ref<QaApp[]>([])
/** 当前选中的应用 ID。 */
const appId = ref('')
/** 当前应用下的会话列表。 */
const sessions = ref<ChatSession[]>([])
/** 当前打开的会话 ID。 */
const sessionId = ref('')
/** 主区消息列表。 */
const messages = ref<UiMsg[]>([])
/** 输入框文案。 */
const input = ref('')
/** 是否正在 SSE 发送中。 */
const sending = ref(false)
/** 会话列表加载中（侧栏 v-loading）。 */
const loadingSessions = ref(false)
/** 当前流式请求的 AbortController；换问/卸载时 abort。 */
let streamAbort: AbortController | null = null

/**
 * 组件卸载：取消进行中的 SSE，避免回调写已销毁状态。
 */
onUnmounted(() => {
  streamAbort?.abort()
  streamAbort = null
})

/** 消息列表 DOM，用于滚到底部。 */
const listRef = ref<HTMLElement | null>(null)

/** 当前应用对象（底部 tip 展示名称）。 */
const currentApp = computed(() => apps.value.find((a) => a.id === appId.value))

/**
 * 校验本地是否有可用 JWT（排除历史 local-* 演示 token）。
 * @returns true 可继续调业务 API；false 已提示请先登录
 */
function ensureJwt() {
  const token = getWebToken() || ''
  // 无 token 或仍是演示前缀：拦截并提示登录
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录')
    return false
  }
  return true
}

/**
 * 从引用片段对象取展示文案（兼容 content / text / content_preview）。
 */
function sourceText(row: Record<string, unknown>) {
  const v = row.content ?? row.text ?? row.content_preview
  return v != null ? String(v) : '—'
}

/**
 * 组装助手消息元信息行：结论标签 · 步数 · 检索/改写次数。
 */
function metaLine(m: UiMsg) {
  const parts: string[] = []
  parts.push(m.conclusionLabel || '正常回答')
  // 有步数才追加
  if (m.stepCount != null) parts.push(`共 ${m.stepCount} 步`)
  // 有检索轮次才追加
  if (m.retrievalRounds != null) parts.push(`检索 ${m.retrievalRounds} 次`)
  // 有改写轮次才追加
  if (m.rewriteRounds != null) parts.push(`改写 ${m.rewriteRounds} 次`)
  return parts.join(' · ')
}

/**
 * 下一帧后将消息列表滚到最底部（流式增量时频繁调用）。
 */
async function scrollBottom() {
  await nextTick()
  // 列表已挂载才改 scrollTop
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

/**
 * 拉取问答应用列表；无选中项时默认选第一项。
 */
async function loadApps() {
  // 未登录则不请求
  if (!ensureJwt()) return
  // GET /api/v1/qa-apps
  apps.value = (await listQaApps()) || []
  // 尚未选应用且列表非空：默认第一项（会触发 watch(appId)）
  if (!appId.value && apps.value.length) appId.value = apps.value[0].id
}

/**
 * 按当前 appId 刷新侧栏会话列表。
 */
async function loadSessions() {
  // 未登录或未选应用：清空侧栏
  if (!ensureJwt() || !appId.value) {
    sessions.value = []
    return
  }
  loadingSessions.value = true
  try {
    // GET /api/v1/chat/sessions?appId=...
    sessions.value = (await listChatSessions(appId.value)) || []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载会话失败')
  } finally {
    loadingSessions.value = false
  }
}

/**
 * 打开指定会话并加载历史消息到主区。
 */
async function openSession(id: string) {
  // 空 id 或正在发送中：忽略点击，避免打断流式
  if (!id || sending.value) return
  sessionId.value = id
  try {
    // GET .../sessions/{id}/messages
    const rows = (await listChatMessages(id)) || []
    // 服务端 role 转 UI role；仅 ASSISTANT 保留 messageId 供赞踩
    messages.value = rows.map((r) => ({
      id: r.id,
      role: String(r.role || '').toUpperCase() === 'USER' ? 'user' : 'assistant',
      content: r.content || '',
      messageId: String(r.role || '').toUpperCase() === 'ASSISTANT' ? r.id : undefined,
      feedback: r.feedback,
      sources: r.sources || [],
      sourcesOpen: false,
    }))
    await scrollBottom()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载消息失败')
  }
}

/**
 * 在当前应用下新建空会话并切到该会话。
 */
async function newChat() {
  // 未登录拦截
  if (!ensureJwt()) return
  // 未选应用无法建会话
  if (!appId.value) {
    ElMessage.warning('请选择问答应用')
    return
  }
  try {
    // POST /api/v1/chat/sessions { appId, clientSource: WEB }
    const s = await createChatSession(appId.value)
    // 刷新侧栏后选中新会话、清空主区
    await loadSessions()
    sessionId.value = s.id
    messages.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '新建对话失败')
  }
}

/**
 * 切换应用：清空当前会话/消息，拉会话列表；有历史则打开最新，否则自动新建。
 */
watch(appId, async (id, prev) => {
  // 空值或未变化：不处理（含首次同值）
  if (!id || id === prev) return
  sessionId.value = ''
  messages.value = []
  // 拉该应用下会话
  await loadSessions()
  // 有会话则打开第一条（通常为最近）
  if (sessions.value.length) {
    await openSession(sessions.value[0].id)
  } else {
    // 无历史：自动建一条空会话
    await newChat()
  }
})

/**
 * 发送问题：本地先插入 user/assistant 气泡，再走 SSE chatAppStream。
 */
async function send() {
  const q = input.value.trim()
  // 空内容或已在发送：直接返回
  if (!q || sending.value) return
  // JWT 校验
  if (!ensureJwt()) return
  // 必须选应用
  if (!appId.value) {
    ElMessage.warning('请选择问答应用')
    return
  }
  // 尚无 sessionId：先建会话
  if (!sessionId.value) await newChat()

  sending.value = true
  input.value = ''
  // 乐观插入用户消息 + 占位助手气泡（reactive 便于 SSE 回调就地改字段）
  const userMsg: UiMsg = { id: `u_${Date.now()}`, role: 'user', content: q }
  const botMsg: UiMsg = reactive({
    id: `a_${Date.now()}`,
    role: 'assistant',
    content: '',
    statusText: '处理中…',
    sources: [],
    sourcesOpen: false,
    streaming: true,
  })
  messages.value.push(userMsg, botMsg)
  await scrollBottom()

  // 取消上一次未完成流，再开新 AbortController
  streamAbort?.abort()
  streamAbort = new AbortController()
  try {
    // POST /api/v1/chat/stream（SSE）：按事件回调刷新气泡
    await chatAppStream(
      { appId: appId.value, sessionId: sessionId.value || undefined, question: q },
      {
        // session：服务端可能回填/校正 sessionId
        onSession: (p) => {
          if (p?.sessionId) sessionId.value = p.sessionId
        },
        // status：过程文案（检索中 / 生成中等）
        onStatus: (text) => {
          botMsg.statusText = text
          void scrollBottom()
        },
        // sources：中途或最终前推送的引用来源
        onSources: (sources) => {
          botMsg.sources = sources || []
          void scrollBottom()
        },
        // delta：增量回答文本
        onDelta: (text) => {
          botMsg.content += text
          // 一旦有正文就清掉「处理中」状态行
          if (botMsg.content) botMsg.statusText = ''
          void scrollBottom()
        },
        // final：定稿答案、messageId、Agent 元信息；并刷新侧栏标题等
        onFinal: (payload) => {
          botMsg.messageId = String(payload.messageId || '')
          botMsg.content = String(payload.answer || botMsg.content || '')
          botMsg.sources = (payload.sources as Array<Record<string, unknown>>) || botMsg.sources
          botMsg.conclusionLabel = payload.conclusionLabel as string | undefined
          botMsg.stepCount = payload.stepCount as number | undefined
          botMsg.retrievalRounds = payload.retrievalRounds as number | undefined
          botMsg.rewriteRounds = payload.rewriteRounds as number | undefined
          botMsg.statusText = ''
          botMsg.streaming = false
          // final 也可能带回 sessionId
          if (payload.sessionId) sessionId.value = String(payload.sessionId)
          void loadSessions()
        },
        // error：业务错误事件（非 HTTP 异常）
        onError: (message) => {
          botMsg.statusText = ''
          botMsg.content = botMsg.content || `出错了：${message}`
          botMsg.streaming = false
        },
      },
      { signal: streamAbort.signal },
    )
  } catch (e) {
    // 主动 abort：不改气泡、直接结束
    if (e instanceof DOMException && e.name === 'AbortError') return
    botMsg.statusText = ''
    botMsg.content = e instanceof Error ? e.message : '发送失败'
    botMsg.streaming = false
  } finally {
    sending.value = false
    await scrollBottom()
  }
}

/**
 * 输入框键盘：Enter 发送，Shift+Enter 换行（不拦截）。
 */
function onKeydown(e: KeyboardEvent) {
  // 单独 Enter：阻止默认换行并发送
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void send()
  }
}

/**
 * 赞/踩切换：同态再点则取消（传空 feedback）。
 */
async function setFeedback(m: UiMsg, fb: 'LIKE' | 'DISLIKE') {
  // 无服务端 messageId 无法写反馈（流式未 final）
  if (!m.messageId) return
  // 再次点击同一反馈 → 取消
  const next = m.feedback === fb ? '' : fb
  try {
    // PUT .../messages/{id}/feedback
    await feedbackChatMessage(m.messageId, next as 'LIKE' | 'DISLIKE' | '')
    m.feedback = next
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '反馈失败')
  }
}

/**
 * 挂载：拉取应用列表（选中后由 watch 拉会话）。
 */
onMounted(async () => {
  try {
    await loadApps()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载应用失败')
  }
})
</script>

<template>
  <!-- 左会话栏 + 右聊天主区 -->
  <div class="chat-page">
    <!-- 侧栏：选应用 / 新建 / 会话列表 -->
    <aside class="sidebar">
      <label class="field-label">问答应用</label>
      <el-select v-model="appId" filterable placeholder="请选择问答应用" style="width: 100%">
        <el-option v-for="a in apps" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>

      <el-button type="primary" class="new-btn" :icon="Plus" @click="newChat">新建对话</el-button>

      <div class="section-title">我的对话</div>
      <!-- 会话列表：点击 openSession -->
      <div v-loading="loadingSessions" class="session-list">
        <button
          v-for="s in sessions"
          :key="s.id"
          type="button"
          class="session-item"
          :class="{ active: s.id === sessionId }"
          @click="openSession(s.id)"
        >
          <span class="title">{{ s.title || '新对话' }}</span>
          <span class="time">{{ (s.updateTime || s.createTime || '').slice(0, 10) }}</span>
        </button>
        <div v-if="!sessions.length" class="empty-side">暂无对话，点击上方新建</div>
      </div>
    </aside>

    <!-- 主区：消息流 + 输入框 -->
    <section class="chat-main">
      <div ref="listRef" class="messages">
        <!-- 空态欢迎 -->
        <div v-if="!messages.length" class="welcome">
          <h2>开始提问吧</h2>
          <p>回答基于企业知识库检索生成，每条都会附上引用来源</p>
        </div>

        <!-- 消息行：用户右对齐 / 助手左对齐 -->
        <div v-for="m in messages" :key="m.id" class="row" :class="m.role">
          <div v-if="m.role === 'user'" class="bubble user">{{ m.content }}</div>
          <div v-else class="assistant">
            <!-- 流式过程状态 -->
            <div v-if="m.statusText" class="status">{{ m.statusText }}</div>
            <div v-if="m.content" class="bubble bot">
              <pre>{{ m.content }}</pre>
            </div>
            <!-- Agent 元信息：结束后才显示 -->
            <div
              v-if="!m.streaming && (m.stepCount != null || m.conclusionLabel || m.retrievalRounds != null)"
              class="meta"
            >
              {{ metaLine(m) }}
            </div>
            <!-- 引用来源折叠 -->
            <button
              v-if="m.sources && m.sources.length"
              type="button"
              class="sources-toggle"
              @click="m.sourcesOpen = !m.sourcesOpen"
            >
              引用来源（{{ m.sources.length }} 条）
              <el-icon :size="14"><ArrowRight :class="{ open: m.sourcesOpen }" /></el-icon>
            </button>
            <div v-if="m.sourcesOpen" class="sources">
              <div v-for="(s, i) in m.sources" :key="i" class="source-item">
                <strong>#{{ i + 1 }}</strong>
                <pre>{{ sourceText(s) }}</pre>
              </div>
            </div>
            <!-- 赞踩：需 messageId 且非流式中 -->
            <div v-if="!m.streaming && m.messageId" class="feedback">
              <button type="button" :class="{ on: m.feedback === 'LIKE' }" @click="setFeedback(m, 'LIKE')">
                👍 有帮助
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
        </div>
      </div>

      <!-- 输入区：回车发送 -->
      <div class="composer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入问题，回车发送，Shift + 回车换行"
          :disabled="sending || !appId"
          @keydown="onKeydown"
        />
        <el-button
          type="primary"
          circle
          class="send"
          :icon="Promotion"
          :loading="sending"
          :disabled="!input.trim() || !appId"
          @click="send"
        />
      </div>
      <p v-if="currentApp" class="app-tip">当前应用：{{ currentApp.name }}</p>
    </section>
  </div>
</template>

<style scoped>
/* 左右分栏聊天页 */
.chat-page {
  height: 100%;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 0;
  background: #f3f6fa;
}
.sidebar {
  background: #fff;
  border-right: 1px solid #e8edf3;
  padding: 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}
.field-label {
  font-size: 12px;
  color: #8a97a6;
}
.new-btn {
  width: 100%;
}
.section-title {
  margin-top: 8px;
  font-size: 13px;
  color: #5b6b7c;
  font-weight: 600;
}
.session-list {
  flex: 1;
  overflow: auto;
  display: grid;
  gap: 6px;
  align-content: start;
}
.session-item {
  border: 0;
  background: transparent;
  text-align: left;
  padding: 10px 10px;
  border-radius: 10px;
  cursor: pointer;
  display: grid;
  gap: 4px;
}
.session-item:hover {
  background: #f5f8fc;
}
.session-item.active {
  background: #eaf2ff;
}
.session-item .title {
  font-size: 13px;
  color: #1f2a37;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-item .time {
  font-size: 11px;
  color: #9aa7b5;
}
.empty-side {
  font-size: 12px;
  color: #9aa7b5;
  padding: 8px;
}
.chat-main {
  display: grid;
  grid-template-rows: 1fr auto auto;
  min-width: 0;
  min-height: 0;
  padding: 16px 20px 12px;
}
.messages {
  overflow: auto;
  background: #fff;
  border: 1px solid #e8edf3;
  border-radius: 14px;
  padding: 20px;
  min-height: 0;
}
.welcome {
  height: 100%;
  min-height: 280px;
  display: grid;
  place-content: center;
  text-align: center;
  gap: 8px;
  color: #6b7785;
}
.welcome h2 {
  margin: 0;
  color: #1f2a37;
  font-size: 22px;
}
.welcome p {
  margin: 0;
  font-size: 13px;
}
.row {
  display: flex;
  margin-bottom: 14px;
}
.row.user {
  justify-content: flex-end;
}
.row.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: min(720px, 86%);
  border-radius: 12px;
  padding: 10px 14px;
  line-height: 1.55;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble.user {
  background: #2f6bff;
  color: #fff;
}
.bubble.bot {
  background: #f3f5f8;
  color: #1f2a37;
}
.bubble.bot pre,
.source-item pre {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
}
.assistant {
  max-width: min(760px, 92%);
  display: grid;
  gap: 8px;
}
.status {
  font-size: 12px;
  color: #8a97a6;
}
.meta {
  font-size: 12px;
  color: #8a97a6;
}
.sources-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: #2f6bff;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
  width: fit-content;
}
.sources-toggle .open {
  transform: rotate(90deg);
}
.sources {
  display: grid;
  gap: 8px;
}
.source-item {
  background: #f8fafc;
  border: 1px solid #e8edf3;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 12px;
  color: #4b5968;
}
.feedback {
  display: flex;
  gap: 8px;
}
.feedback button {
  border: 1px solid #e1e7ef;
  background: #fff;
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  color: #5b6b7c;
}
.feedback button.on {
  border-color: #2f6bff;
  color: #2f6bff;
  background: #eaf2ff;
}
.composer {
  position: relative;
  margin-top: 12px;
}
.composer :deep(.el-textarea__inner) {
  padding-right: 52px;
  border-radius: 12px;
  min-height: 84px !important;
}
.send {
  position: absolute;
  right: 12px;
  bottom: 12px;
}
.app-tip {
  margin: 6px 0 0;
  font-size: 12px;
  color: #9aa7b5;
  text-align: center;
}
@media (max-width: 900px) {
  .chat-page {
    grid-template-columns: 1fr;
  }
  .sidebar {
    max-height: 220px;
  }
}
</style>
