import { clearWebToken, getWebToken, setWebToken, setWebUser, type WebUser } from './auth'

/** 网关统一业务响应体：code=0 表示成功，data 为业务载荷。 */
export type ApiResult<T> = { code: number; message: string; data: T }

/**
 * 将 fetch Response 解析为 ApiResult。
 * - 空 body：按 HTTP 状态区分「未登录」与「空响应」错误
 * - 非空 body：尝试 JSON.parse；失败则截断原文抛错
 */
async function readApiResult<T>(res: Response): Promise<ApiResult<T>> {
  // 先读文本，避免对空 body / 非 JSON 直接 json() 抛出难读错误
  const text = await res.text()
  // 空响应：无法解析业务码，只能依赖 HTTP 状态
  if (!text) {
    // 401/403 视为登录失效，清本地 token 后提示重新登录
    if (res.status === 401 || res.status === 403) {
      clearWebToken()
      throw new Error('未登录或登录已失效，请重新登录')
    }
    throw new Error(`服务返回空响应（HTTP ${res.status}）`)
  }
  // 尝试按 JSON 业务体解析
  try {
    return JSON.parse(text) as ApiResult<T>
  } catch {
    // 网关/代理偶发返回 HTML/纯文本时给出可读截断提示
    throw new Error(`服务响应不是 JSON（HTTP ${res.status}）：${text.slice(0, 120)}`)
  }
}

/**
 * 组装请求头：合并调用方额外头，并在存在 Web JWT 时附加 Authorization。
 */
function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = { ...(extra || {}) }
  const token = getWebToken()
  // 有 token 才带 Bearer，避免匿名请求误传空 Authorization
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

/**
 * 校验 HTTP + 业务 code；失败时抛 Error，成功时返回 json.data。
 * label 用于拼默认错误文案（如 request failed 401）。
 */
function assertBizOk<T>(res: Response, json: ApiResult<T>, label: string): T {
  // HTTP 或业务码任一表示未授权：清 token 并统一提示
  if (res.status === 401 || res.status === 403 || json.code === 401 || json.code === 403) {
    clearWebToken()
    throw new Error(json.message || '未登录或登录已失效，请重新登录')
  }
  // 其它非 2xx 或业务失败：抛出服务端 message
  if (!res.ok || json.code !== 0) {
    throw new Error(json.message || `${label} failed ${res.status}`)
  }
  return json.data
}

/**
 * 带鉴权的 GET：解析 ApiResult 并返回 data。
 */
async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(path, { headers: authHeaders() })
  const json = await readApiResult<T>(res)
  return assertBizOk(res, json, 'request')
}

/**
 * 带鉴权的 POST：JSON body（可省略）；解析 ApiResult 并返回 data。
 */
async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    // undefined body 时不序列化，避免发出字面量 "undefined"
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await readApiResult<T>(res)
  return assertBizOk(res, json, 'request')
}

/**
 * 带鉴权的 PUT：JSON body（可省略）；解析 ApiResult 并返回 data。
 */
async function apiPut<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'PUT',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await readApiResult<T>(res)
  return assertBizOk(res, json, 'request')
}

/** 登录接口返回：JWT + 用户信息（写入 localStorage）。 */
export type LoginData = {
  accessToken: string
  tokenType?: string
  expiresIn?: number
  user: WebUser
}

/**
 * 用户端登录：成功后落盘 accessToken 与 user，供后续鉴权请求使用。
 */
export async function login(username: string, password: string) {
  const data = await apiPost<LoginData>('/api/v1/auth/login', { username, password })
  setWebToken(data.accessToken)
  setWebUser(data.user)
  return data
}

/** 前台可选的问答应用摘要（来自 /qa-apps）。 */
export type QaApp = {
  id: string
  name: string
  knowledgeBaseId?: string
  enableAgentic?: number
  enabled?: number
  fallbackReply?: string
}

/** 聊天会话摘要。 */
export type ChatSession = {
  id: string
  appId: string
  title?: string
  createTime?: string
  updateTime?: string
}

/** 会话内单条消息（含可选 sources / 赞踩）。 */
export type ChatMessage = {
  id: string
  sessionId?: string
  role: string
  content: string
  feedback?: string
  createTime?: string
  sources?: Array<Record<string, unknown>>
  agentRunId?: string
}

/**
 * 列出当前用户可用的问答应用。
 */
export function listQaApps() {
  return apiGet<QaApp[]>('/api/v1/qa-apps')
}

/**
 * 在指定应用下新建会话；clientSource=WEB 标记来源为用户前台。
 */
export function createChatSession(appId: string) {
  return apiPost<ChatSession>('/api/v1/chat/sessions', { appId, clientSource: 'WEB' })
}

/**
 * 按应用 ID 拉取会话列表（appId 已 URL 编码）。
 */
export function listChatSessions(appId: string) {
  return apiGet<ChatSession[]>(`/api/v1/chat/sessions?appId=${encodeURIComponent(appId)}`)
}

/**
 * 拉取某会话下的历史消息（含 sources / feedback）。
 */
export function listChatMessages(sessionId: string) {
  return apiGet<ChatMessage[]>(`/api/v1/chat/sessions/${encodeURIComponent(sessionId)}/messages`)
}

/**
 * 对助手消息赞/踩；传空字符串表示取消反馈（服务端收 null）。
 */
export function feedbackChatMessage(messageId: string, feedback: 'LIKE' | 'DISLIKE' | '') {
  return apiPut<null>(`/api/v1/chat/messages/${encodeURIComponent(messageId)}/feedback`, {
    feedback: feedback || null,
  })
}

/**
 * SSE 流式问答回调集合。
 * 各事件由 chatAppStream 按 event 名分发。
 */
export type ChatStreamHandlers = {
  onSession?: (payload: { sessionId: string; appId: string }) => void
  onStatus?: (text: string) => void
  onSources?: (sources: Array<Record<string, unknown>>) => void
  onDelta?: (text: string) => void
  onFinal?: (payload: Record<string, unknown>) => void
  onError?: (message: string) => void
}

/**
 * 发起 SSE 聊天流：POST /api/v1/chat/stream，按 SSE 帧解析并回调 handlers。
 *
 * 解析分支概要：
 * 1) HTTP 非 ok 或无 body → 读文本抛错
 * 2) 循环 reader.read()：signal 已 abort 则 cancel；done 则结束
 * 3) 按空行拆帧；帧内解析 event: / data: 行
 * 4) data 优先 JSON.parse，失败保留原始字符串
 * 5) 按 event 名调用 onSession/onStatus/onSources/onDelta/onFinal/onError
 * 6) AbortError / signal.aborted 静默返回，其它异常上抛
 */
export async function chatAppStream(
  payload: { appId: string; sessionId?: string; question: string },
  handlers: ChatStreamHandlers,
  opts?: { signal?: AbortSignal },
) {
  const token = getWebToken()
  // 直接 fetch SSE，Accept 声明 event-stream；可选 Bearer
  const res = await fetch('/api/v1/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
    signal: opts?.signal,
  })
  // 无流式 body 或 HTTP 失败：无法继续读帧
  if (!res.ok || !res.body) {
    const text = await res.text()
    throw new Error(text || `chat stream failed ${res.status}`)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  // 跨 read 累积的半包缓冲（SSE 以 \n\n 分帧）
  let buffer = ''
  try {
    // 持续拉取字节直到流结束或 abort
    while (true) {
      // 外部取消：主动 cancel reader 并跳出
      if (opts?.signal?.aborted) {
        await reader.cancel()
        break
      }
      const { done, value } = await reader.read()
      // 对端关闭流
      if (done) break
      // 增量解码并拼入缓冲
      buffer += decoder.decode(value, { stream: true })
      // 按空行切完整帧；最后一段可能不完整，留在 buffer
      const chunks = buffer.split('\n\n')
      buffer = chunks.pop() || ''
      // 逐帧解析 event / data
      for (const chunk of chunks) {
        const lines = chunk.split('\n')
        let event = 'message'
        const dataLines: string[] = []
        // 解析单帧内的 SSE 行协议
        for (const line of lines) {
          // event: 指定事件名（默认 message）
          if (line.startsWith('event:')) event = line.slice(6).trim()
          // data: 可多行，稍后 join
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
        }
        // 无 data 的心跳/空帧跳过
        if (!dataLines.length) continue
        const raw = dataLines.join('\n')
        let data: any = raw
        // 优先当 JSON；失败则保留字符串供下游 String(data)
        try {
          data = JSON.parse(raw)
        } catch {
          /* keep raw string */
        }
        // 按事件名分发到业务回调
        if (event === 'session') handlers.onSession?.(data)
        else if (event === 'status') handlers.onStatus?.(data?.text || String(data))
        else if (event === 'sources') handlers.onSources?.(data?.sources || [])
        else if (event === 'delta') {
          // delta 载荷字段不统一：content / text / 纯字符串
          const piece =
            typeof data?.content === 'string'
              ? data.content
              : typeof data?.text === 'string'
                ? data.text
                : typeof data === 'string'
                  ? data
                  : ''
          // 非空片段才推给 UI，避免空白闪烁
          if (piece) handlers.onDelta?.(piece)
        } else if (event === 'final') handlers.onFinal?.(data)
        else if (event === 'error') handlers.onError?.(data?.message || String(data))
      }
    }
  } catch (e) {
    // 用户主动取消：不当错误上抛
    if (opts?.signal?.aborted || (e instanceof DOMException && e.name === 'AbortError')) {
      return
    }
    throw e
  }
}
