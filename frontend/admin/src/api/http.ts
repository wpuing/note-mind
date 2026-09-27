import { getAdminToken, clearAdminToken } from '@/api/ai'
import { useAppStore } from '@/stores/app'

export type ApiResult<T> = {
  code: number
  message: string
  data: T
}
async function readApiResult<T>(res: Response): Promise<ApiResult<T>> {
  const text = await res.text()
  if (!text) {
    if (res.status === 401 || res.status === 403) {
      throwUnauthorized()
    }
    throw new Error(`服务返回空响应（HTTP ${res.status}）`)
  }
  try {
    return JSON.parse(text) as ApiResult<T>
  } catch {
    throw new Error(`服务响应不是 JSON（HTTP ${res.status}）：${text.slice(0, 120)}`)
  }
}

function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = { ...(extra || {}) }
  const token = getAdminToken()
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

function isUnauthorized(res: Response, json: ApiResult<unknown>) {
  return res.status === 401 || res.status === 403 || json.code === 401 || json.code === 403
}

/** 401/403：清 JWT + Pinia 用户态，避免本地残留假登录态 */
function throwUnauthorized(message?: string): never {
  clearAdminToken()
  try {
    useAppStore().clearCurrentUser()
  } catch {
    try {
      localStorage.removeItem('notemind_admin_user')
    } catch {
      /* ignore */
    }
  }
  throw new Error(message || '未登录或登录已失效，请重新登录')
}

function assertBizOk<T>(res: Response, json: ApiResult<T>, label: string): T {
  if (isUnauthorized(res, json)) {
    throwUnauthorized(json.message)
  }
  if (!res.ok || json.code !== 0) {
    throw new Error(json.message || `${label} failed ${res.status}`)
  }
  return json.data
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await readApiResult<T>(res)
  return assertBizOk(res, json, 'request')
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(path, { headers: authHeaders() })
  const json = await readApiResult<T>(res)
  return assertBizOk(res, json, 'request')
}

export async function apiPut<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'PUT',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await readApiResult<T>(res)
  return assertBizOk(res, json, 'request')
}

export async function apiDelete<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'DELETE',
    headers: authHeaders(body !== undefined ? { 'Content-Type': 'application/json' } : undefined),
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const json = await readApiResult<T>(res)
  return assertBizOk(res, json, 'request')
}

/** multipart 上传（勿手动设置 Content-Type，由浏览器带 boundary） */
export async function apiUpload<T>(path: string, form: FormData): Promise<T> {
  const res = await fetch(path, { method: 'POST', headers: authHeaders(), body: form })
  const json = await readApiResult<T>(res)
  return assertBizOk(res, json, 'upload')
}

export type KnowledgeBase = {
  id: string
  name: string
  category?: string
  description?: string
  status?: number
  embeddingModelId?: string
  chunkStrategyId?: string
  retrievalStrategyId?: string
  documentCount?: number
  segmentCount?: number
  createTime?: string
  /** READY / PARTIAL / EMPTY / FAILED */
  vectorStatus?: string
  vectorDoneCount?: number
  vectorPendingCount?: number
  vectorFailedCount?: number
}

export type KnowledgeBaseSavePayload = {
  name: string
  category?: string
  description?: string
  status?: number
  embeddingModelId?: string
  chunkStrategyId?: string
  retrievalStrategyId?: string
}

export type KnowledgeBaseListQuery = {
  name?: string
  status?: number | ''
  category?: string
  page?: number
  pageSize?: number
}

export type KnowledgeCategory = {
  id: string
  code: string
  name: string
  sortNo?: number
  status?: number
  createTime?: string
  refCount?: number
}

export type KnowledgeCategorySavePayload = {
  code: string
  name: string
  sortNo?: number
  status?: number
}

export type KnowledgeCategoryListQuery = {
  keyword?: string
  status?: number | ''
  page?: number
  pageSize?: number
}

export type KnowledgeDocument = {
  id: string
  knowledgeBaseId: string
  knowledgeBaseName?: string
  title: string
  fileName: string
  fileType: string
  fileSize?: number
  parseStatus: string
  errorMessage?: string
  segmentCount?: number
  charCount?: number
  chunkSize?: number
  chunkOverlap?: number
  chunkStrategyId?: string
  strategyType?: string
  createTime?: string
  vectorDoneCount?: number
  vectorPendingCount?: number
  vectorFailedCount?: number
  vectorStatus?: string
}

export type DocumentParsedText = {
  documentId: string
  title?: string
  fileName?: string
  fileType?: string
  charCount?: number
  text: string
  hint?: string
}

export type ChunkStrategy = {
  id: string
  name: string
  strategyType: string
  chunkSize?: number
  chunkOverlap?: number
  parentChunkSize?: number
  childChunkSize?: number
  childOverlap?: number
  separators?: string[]
  enabled?: number
  isDefault?: number
  remark?: string
  createTime?: string
  refCount?: number
}

export type ChunkStrategySavePayload = {
  name: string
  strategyType: string
  chunkSize?: number
  chunkOverlap?: number
  parentChunkSize?: number
  childChunkSize?: number
  childOverlap?: number
  separators?: string[]
  enabled?: number
  isDefault?: number
  remark?: string
}

export type ChunkStrategyListQuery = {
  name?: string
  strategyType?: string
  page?: number
  pageSize?: number
}

export type KnowledgeDocumentVector = {
  documentId: string
  title: string
  fileName?: string
  parseStatus?: string
  errorMessage?: string
  segmentCount?: number
  vectorDoneCount?: number
  vectorPendingCount?: number
  vectorFailedCount?: number
  vectorStatus?: string
  createTime?: string
}

export type DocumentListQuery = {
  knowledgeBaseId?: string
  title?: string
  parseStatus?: string
  /** PENDING | FAILED | PARTIAL — 按片段向量问题筛选文档 */
  vectorIssue?: string
  page?: number
  pageSize?: number
}

export type PageResult<T> = {
  total: number
  page: number
  pageSize: number
  records: T[]
}

export function listKnowledgeBases() {
  return apiGet<KnowledgeBase[]>('/api/v1/knowledge/bases')
}

export function pageKnowledgeBases(query: KnowledgeBaseListQuery = {}) {
  const q = new URLSearchParams()
  if (query.name) q.set('name', query.name)
  if (query.status !== undefined && query.status !== '') q.set('status', String(query.status))
  if (query.category) q.set('category', query.category)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 8))
  return apiGet<PageResult<KnowledgeBase>>(`/api/v1/knowledge/bases/page?${q}`)
}

export function listKnowledgeCategories() {
  return apiGet<KnowledgeCategory[]>('/api/v1/knowledge/categories')
}

export function pageKnowledgeCategories(query: KnowledgeCategoryListQuery = {}) {
  const q = new URLSearchParams()
  if (query.keyword) q.set('keyword', query.keyword)
  if (query.status !== undefined && query.status !== '') q.set('status', String(query.status))
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 8))
  return apiGet<PageResult<KnowledgeCategory>>(`/api/v1/knowledge/categories/page?${q}`)
}

export function createKnowledgeCategory(payload: KnowledgeCategorySavePayload) {
  return apiPost<KnowledgeCategory>('/api/v1/knowledge/categories', payload)
}

export function updateKnowledgeCategory(id: string, payload: KnowledgeCategorySavePayload) {
  return apiPut<KnowledgeCategory>(`/api/v1/knowledge/categories/${encodeURIComponent(id)}`, payload)
}

export function deleteKnowledgeCategory(id: string) {
  return apiDelete<null>(`/api/v1/knowledge/categories/${encodeURIComponent(id)}`)
}

export function createKnowledgeBase(payload: KnowledgeBaseSavePayload) {
  return apiPost<KnowledgeBase>('/api/v1/knowledge/bases', payload)
}

export function updateKnowledgeBase(id: string, payload: KnowledgeBaseSavePayload) {
  return apiPut<KnowledgeBase>(`/api/v1/knowledge/bases/${encodeURIComponent(id)}`, payload)
}

export function deleteKnowledgeBase(id: string) {
  return apiDelete<null>(`/api/v1/knowledge/bases/${encodeURIComponent(id)}`)
}

export function batchDeleteKnowledgeBases(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/knowledge/bases/batch-delete', { ids })
}

export function listKnowledgeBaseVectorDocuments(kbId: string) {
  return apiGet<KnowledgeDocumentVector[]>(
    `/api/v1/knowledge/bases/${encodeURIComponent(kbId)}/vector-documents`,
  )
}

export function getKnowledgeDocument(id: string) {
  return apiGet<KnowledgeDocument>(`/api/v1/knowledge/documents/${encodeURIComponent(id)}`)
}

export function getDocumentParsedText(id: string) {
  return apiGet<DocumentParsedText>(
    `/api/v1/knowledge/documents/${encodeURIComponent(id)}/parsed-text`,
  )
}

export function listDocuments(query: DocumentListQuery = {}) {
  const q = new URLSearchParams()
  if (query.knowledgeBaseId) q.set('knowledgeBaseId', query.knowledgeBaseId)
  if (query.title) q.set('title', query.title)
  if (query.parseStatus) q.set('parseStatus', query.parseStatus)
  if (query.vectorIssue) q.set('vectorIssue', query.vectorIssue)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 8))
  return apiGet<PageResult<KnowledgeDocument>>(`/api/v1/knowledge/documents?${q}`)
}

export function uploadDocument(
  file: File,
  opts: {
    knowledgeBaseId?: string
  } = {},
) {
  const form = new FormData()
  form.append('file', file)
  form.append('knowledgeBaseId', opts.knowledgeBaseId || 'kb_default')
  return apiUpload<KnowledgeDocument>('/api/v1/knowledge/documents/upload', form)
}

export function chunkDocument(id: string, chunkStrategyId?: string) {
  return apiPost<KnowledgeDocument>(`/api/v1/knowledge/documents/${encodeURIComponent(id)}/chunk`, {
    chunkStrategyId: chunkStrategyId || undefined,
  })
}

export function vectorizeDocument(id: string) {
  return apiPost<KnowledgeDocument>(
    `/api/v1/knowledge/documents/${encodeURIComponent(id)}/vectorize`,
  )
}

export function clearDocumentVectors(id: string) {
  return apiDelete<KnowledgeDocument>(
    `/api/v1/knowledge/documents/${encodeURIComponent(id)}/vectors`,
  )
}

export function updateDocument(id: string, payload: { title: string }) {
  return apiPut<KnowledgeDocument>(
    `/api/v1/knowledge/documents/${encodeURIComponent(id)}`,
    payload,
  )
}

export function deleteDocument(id: string) {
  return apiDelete<null>(`/api/v1/knowledge/documents/${encodeURIComponent(id)}`)
}

export function batchDeleteDocuments(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/knowledge/documents/batch-delete', { ids })
}

export function reuploadDocument(id: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return apiUpload<KnowledgeDocument>(
    `/api/v1/knowledge/documents/${encodeURIComponent(id)}/reupload`,
    form,
  )
}

export async function downloadDocument(id: string, filename?: string) {
  const res = await fetch(`/api/v1/knowledge/documents/${encodeURIComponent(id)}/download`, {
    headers: authHeaders(),
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || `下载失败 ${res.status}`)
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename || 'download'
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function listChunkStrategies() {
  return apiGet<ChunkStrategy[]>('/api/v1/knowledge/chunk-strategies')
}

export function pageChunkStrategies(query: ChunkStrategyListQuery = {}) {
  const q = new URLSearchParams()
  if (query.name) q.set('name', query.name)
  if (query.strategyType) q.set('strategyType', query.strategyType)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 8))
  return apiGet<PageResult<ChunkStrategy>>(`/api/v1/knowledge/chunk-strategies/page?${q}`)
}

export function createChunkStrategy(payload: ChunkStrategySavePayload) {
  return apiPost<ChunkStrategy>('/api/v1/knowledge/chunk-strategies', payload)
}

export function updateChunkStrategy(id: string, payload: ChunkStrategySavePayload) {
  return apiPut<ChunkStrategy>(
    `/api/v1/knowledge/chunk-strategies/${encodeURIComponent(id)}`,
    payload,
  )
}

export function deleteChunkStrategy(id: string) {
  return apiDelete<null>(`/api/v1/knowledge/chunk-strategies/${encodeURIComponent(id)}`)
}

export function batchDeleteChunkStrategies(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/knowledge/chunk-strategies/batch-delete', { ids })
}

export type RetrievalStrategy = {
  id: string
  name: string
  enableVector?: number
  enableBm25?: number
  enableRrf?: number
  enableRerank?: number
  enableRewrite?: number
  enableParentFill?: number
  topK?: number
  rerankTopN?: number
  vectorTopK?: number
  bm25TopK?: number
  rrfK?: number
  cosineThreshold?: number | null
  rerankThreshold?: number | null
  rewriteMode?: string
  rewriteCount?: number
  enabled?: number
  isDefault?: number
  remark?: string
  createTime?: string
  refCount?: number
}

export type RetrievalStrategySavePayload = {
  name: string
  enableVector?: number
  enableBm25?: number
  enableRrf?: number
  enableRerank?: number
  enableRewrite?: number
  enableParentFill?: number
  topK?: number
  rerankTopN?: number
  vectorTopK?: number
  bm25TopK?: number
  rrfK?: number
  cosineThreshold?: number | null
  rerankThreshold?: number | null
  rewriteMode?: string
  rewriteCount?: number
  enabled?: number
  isDefault?: number
  remark?: string
}

export type RetrievalStrategyListQuery = {
  name?: string
  page?: number
  pageSize?: number
}

export function listRetrievalStrategies() {
  return apiGet<RetrievalStrategy[]>('/api/v1/knowledge/retrieval-strategies')
}

export function pageRetrievalStrategies(query: RetrievalStrategyListQuery = {}) {
  const q = new URLSearchParams()
  if (query.name) q.set('name', query.name)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 8))
  return apiGet<PageResult<RetrievalStrategy>>(
    `/api/v1/knowledge/retrieval-strategies/page?${q}`,
  )
}

export function createRetrievalStrategy(payload: RetrievalStrategySavePayload) {
  return apiPost<RetrievalStrategy>('/api/v1/knowledge/retrieval-strategies', payload)
}

export function updateRetrievalStrategy(id: string, payload: RetrievalStrategySavePayload) {
  return apiPut<RetrievalStrategy>(
    `/api/v1/knowledge/retrieval-strategies/${encodeURIComponent(id)}`,
    payload,
  )
}

export function deleteRetrievalStrategy(id: string) {
  return apiDelete<null>(`/api/v1/knowledge/retrieval-strategies/${encodeURIComponent(id)}`)
}

export function batchDeleteRetrievalStrategies(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/knowledge/retrieval-strategies/batch-delete', {
    ids,
  })
}

export type RetrievalStage = {
  key?: string
  name?: string
  count_text?: string
  countText?: string
  elapsed_ms?: number
  elapsedMs?: number
  detail?: string
  threshold_label?: string
  thresholdLabel?: string
  threshold_hint?: string
  thresholdHint?: string
  score_range?: string
  scoreRange?: string
  not_applicable?: boolean
  notApplicable?: boolean
  explain?: string
}

export type KnowledgeRetrievalTestResult = {
  question?: string
  knowledgeBaseId?: string
  scoreScale?: string
  scoreScaleLabel?: string
  strategyId?: string
  strategyName?: string
  rewrittenQueries?: string[]
  retrievalQueries?: string[]
  sources?: Record<string, unknown>[]
  segments?: Record<string, unknown>[]
  stages?: RetrievalStage[]
  hitCount?: number
  elapsedMs?: number
}

export function knowledgeRetrievalTest(payload: {
  question: string
  knowledgeBaseId?: string
  retrievalStrategyId?: string
  documentIds?: string[]
  chatHistory?: { role: string; content: string }[]
}) {
  return apiPost<KnowledgeRetrievalTestResult>('/api/v1/knowledge/retrieval/test', payload)
}

export type RecallBenchRun = {
  id: string
  question: string
  knowledgeBaseId?: string
  knowledgeBaseName?: string
  strategyCount?: number
  strategyNames?: string[]
  documentIds?: string[]
  elapsedMs?: number
  createTime?: string
  results?: KnowledgeRetrievalTestResult[]
}

export function compareRecallBench(payload: {
  question: string
  knowledgeBaseId: string
  retrievalStrategyIds: string[]
  documentIds?: string[]
}) {
  return apiPost<RecallBenchRun>('/api/v1/knowledge/recall-bench/compare', payload)
}

export function pageRecallBenchRuns(query: {
  knowledgeBaseId?: string
  page?: number
  pageSize?: number
}) {
  const q = new URLSearchParams()
  if (query.knowledgeBaseId) q.set('knowledgeBaseId', query.knowledgeBaseId)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 10))
  return apiGet<PageResult<RecallBenchRun>>(`/api/v1/knowledge/recall-bench/page?${q}`)
}

export function getRecallBenchRun(id: string) {
  return apiGet<RecallBenchRun>(`/api/v1/knowledge/recall-bench/${encodeURIComponent(id)}`)
}

export function deleteRecallBenchRun(id: string) {
  return apiDelete<null>(`/api/v1/knowledge/recall-bench/${encodeURIComponent(id)}`)
}

export function batchDeleteRecallBenchRuns(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/knowledge/recall-bench/batch-delete', { ids })
}

export type KnowledgeSegment = {
  id: string
  knowledgeBaseId?: string
  knowledgeBaseName?: string
  documentId: string
  documentTitle?: string
  parentId?: string
  segmentType?: string
  segmentIndex?: number
  content: string
  contentTokens?: number
  pageNo?: number
  vectorStatus?: string
  vectorId?: string
  manuallyEdited?: number
  createTime?: string
  /** 兼容旧字段 */
  segmentId?: string
  chunkIndex?: number
  contentPreview?: string
  parentSegmentId?: string
}

export type SegmentListQuery = {
  knowledgeBaseId?: string
  documentId?: string
  segmentType?: string
  vectorStatus?: string
  keyword?: string
  page?: number
  pageSize?: number
}

export function listDocumentSegments(
  documentId: string,
  opts: { keyword?: string; page?: number; pageSize?: number } = {},
) {
  const q = new URLSearchParams()
  if (opts.keyword) q.set('keyword', opts.keyword)
  q.set('page', String(opts.page ?? 1))
  q.set('pageSize', String(opts.pageSize ?? 8))
  return apiGet<PageResult<KnowledgeSegment>>(
    `/api/v1/knowledge/documents/${encodeURIComponent(documentId)}/segments?${q}`,
  )
}

export function listSegments(query: SegmentListQuery = {}) {
  const q = new URLSearchParams()
  if (query.knowledgeBaseId) q.set('knowledgeBaseId', query.knowledgeBaseId)
  if (query.documentId) q.set('documentId', query.documentId)
  if (query.segmentType) q.set('segmentType', query.segmentType)
  if (query.vectorStatus) q.set('vectorStatus', query.vectorStatus)
  if (query.keyword) q.set('keyword', query.keyword)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 8))
  return apiGet<PageResult<KnowledgeSegment>>(`/api/v1/knowledge/segments?${q}`)
}

export function updateSegment(id: string, payload: { content: string }) {
  return apiPut<KnowledgeSegment>(
    `/api/v1/knowledge/segments/${encodeURIComponent(id)}`,
    payload,
  )
}

export function deleteSegment(id: string) {
  return apiDelete<null>(`/api/v1/knowledge/segments/${encodeURIComponent(id)}`)
}

export function batchDeleteSegments(ids: string[]) {
  return apiPost<number>('/api/v1/knowledge/segments/batch-delete', { ids })
}

export type AiModelConfig = {
  id: string
  name: string
  modelType: string
  provider?: string
  modelName: string
  baseUrl?: string
  apiStyle?: string
  apiKeyMasked?: string
  hasApiKey?: boolean
  dimension?: number | null
  temperature?: number | null
  enabled?: number
  remark?: string
  createTime?: string
}

export type AiModelConfigSavePayload = {
  name: string
  modelType: string
  provider?: string
  modelName: string
  baseUrl?: string
  apiStyle?: string
  apiKey?: string
  dimension?: number | null
  temperature?: number | null
  enabled?: number
  remark?: string
}

export type AiModelConfigListQuery = {
  name?: string
  modelType?: string
  page?: number
  pageSize?: number
}

export type AiModelConfigTestResult = {
  ok: boolean
  message?: string
  httpStatus?: number
  elapsedMs?: number
}

export function pageAiModels(query: AiModelConfigListQuery = {}) {
  const q = new URLSearchParams()
  if (query.name) q.set('name', query.name)
  if (query.modelType) q.set('modelType', query.modelType)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 8))
  return apiGet<PageResult<AiModelConfig>>(`/api/v1/ai-models/page?${q}`)
}

export function listAiModels() {
  return apiGet<AiModelConfig[]>('/api/v1/ai-models')
}

export function createAiModel(payload: AiModelConfigSavePayload) {
  return apiPost<AiModelConfig>('/api/v1/ai-models', payload)
}

export function updateAiModel(id: string, payload: AiModelConfigSavePayload) {
  return apiPut<AiModelConfig>(`/api/v1/ai-models/${encodeURIComponent(id)}`, payload)
}

export function deleteAiModel(id: string) {
  return apiDelete<null>(`/api/v1/ai-models/${encodeURIComponent(id)}`)
}

export function batchDeleteAiModels(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/ai-models/batch-delete', { ids })
}

export function testAiModel(id: string) {
  return apiPost<AiModelConfigTestResult>(`/api/v1/ai-models/${encodeURIComponent(id)}/test`, {})
}

export type PromptTemplate = {
  id: string
  code: string
  name: string
  scenario?: string
  content: string
  variables?: string
  enabled?: number
  remark?: string
  createTime?: string
}

export type PromptTemplateSavePayload = {
  code: string
  name: string
  scenario?: string
  content: string
  enabled?: number
  remark?: string
}

export type PromptTemplateListQuery = {
  name?: string
  scenario?: string
  page?: number
  pageSize?: number
}

export function pagePromptTemplates(query: PromptTemplateListQuery = {}) {
  const q = new URLSearchParams()
  if (query.name) q.set('name', query.name)
  if (query.scenario) q.set('scenario', query.scenario)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 8))
  return apiGet<PageResult<PromptTemplate>>(`/api/v1/prompt-templates/page?${q}`)
}

export function createPromptTemplate(payload: PromptTemplateSavePayload) {
  return apiPost<PromptTemplate>('/api/v1/prompt-templates', payload)
}

export function updatePromptTemplate(id: string, payload: PromptTemplateSavePayload) {
  return apiPut<PromptTemplate>(`/api/v1/prompt-templates/${encodeURIComponent(id)}`, payload)
}

export function deletePromptTemplate(id: string) {
  return apiDelete<null>(`/api/v1/prompt-templates/${encodeURIComponent(id)}`)
}

export function batchDeletePromptTemplates(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/prompt-templates/batch-delete', { ids })
}

export type AgentTool = {
  id: string
  code: string
  name: string
  description?: string
  schemaJson?: string
  enabled?: number
  sortNo?: number
  implemented?: number
  createTime?: string
}

export type AgentToolSavePayload = {
  code: string
  name: string
  description: string
  schemaJson?: string
  enabled?: number
  sortNo?: number
  implemented?: number
}

export type AgentToolListQuery = {
  name?: string
  enabled?: number
  page?: number
  pageSize?: number
}

export type AgentToolStat = {
  toolCode: string
  toolName?: string
  callCount: number
  successCount: number
  failCount: number
  avgLatencyMs?: number | null
}

export function pageAgentTools(query: AgentToolListQuery = {}) {
  const q = new URLSearchParams()
  if (query.name) q.set('name', query.name)
  if (query.enabled === 0 || query.enabled === 1) q.set('enabled', String(query.enabled))
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 8))
  return apiGet<PageResult<AgentTool>>(`/api/v1/agent-tools/page?${q}`)
}

export function createAgentTool(payload: AgentToolSavePayload) {
  return apiPost<AgentTool>('/api/v1/agent-tools', payload)
}

export function updateAgentTool(id: string, payload: AgentToolSavePayload) {
  return apiPut<AgentTool>(`/api/v1/agent-tools/${encodeURIComponent(id)}`, payload)
}

export function updateAgentToolEnabled(id: string, enabled: number) {
  return apiPut<AgentTool>(`/api/v1/agent-tools/${encodeURIComponent(id)}/enabled`, { enabled })
}

export function deleteAgentTool(id: string) {
  return apiDelete<null>(`/api/v1/agent-tools/${encodeURIComponent(id)}`)
}

export function batchDeleteAgentTools(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/agent-tools/batch-delete', { ids })
}

export function fetchAgentToolOpenaiDefinitions(codes?: string[]) {
  const q = new URLSearchParams()
  if (codes?.length) q.set('codes', codes.join(','))
  const suffix = q.toString() ? `?${q}` : ''
  return apiGet<{ json: string }>(`/api/v1/agent-tools/openai-definitions${suffix}`)
}

export function fetchAgentToolStats(codes?: string[]) {
  const q = new URLSearchParams()
  if (codes?.length) q.set('codes', codes.join(','))
  const suffix = q.toString() ? `?${q}` : ''
  return apiGet<AgentToolStat[]>(`/api/v1/agent-tools/stats${suffix}`)
}

export type AgentToolCallLog = {
  id: string
  toolId?: string
  toolCode: string
  toolName?: string
  agentRunId?: string
  inputJson?: string
  outputJson?: string
  status?: string
  latencyMs?: number | null
  errorMessage?: string
  createTime?: string
}

export type AgentToolCallLogQuery = {
  toolCode?: string
  status?: string
  agentRunId?: string
  page?: number
  pageSize?: number
}

export function pageAgentToolCallLogs(query: AgentToolCallLogQuery = {}) {
  const q = new URLSearchParams()
  if (query.toolCode) q.set('toolCode', query.toolCode)
  if (query.status) q.set('status', query.status)
  if (query.agentRunId) q.set('agentRunId', query.agentRunId)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 10))
  return apiGet<PageResult<AgentToolCallLog>>(`/api/v1/agent-tool-call-logs/page?${q}`)
}

export function getAgentToolCallLog(id: string) {
  return apiGet<AgentToolCallLog>(`/api/v1/agent-tool-call-logs/${encodeURIComponent(id)}`)
}

export function deleteAgentToolCallLog(id: string) {
  return apiDelete<null>(`/api/v1/agent-tool-call-logs/${encodeURIComponent(id)}`)
}

export function batchDeleteAgentToolCallLogs(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/agent-tool-call-logs/batch-delete', { ids })
}

export type AgentStep = {
  id: string
  agentRunId?: string
  stepIndex: number
  nodeName: string
  title?: string
  inputJson?: string
  outputJson?: string
  status?: string
  latencyMs?: number | null
  createTime?: string
}

export type AgentRun = {
  id: string
  runType?: string
  knowledgeBaseId?: string
  question?: string
  finalAnswer?: string
  conclusionLabel?: string
  status?: string
  errorMessage?: string
  totalLatencyMs?: number | null
  stepCount?: number
  retrievalRounds?: number
  rewriteRounds?: number
  sourcesJson?: string
  createTime?: string
  steps?: AgentStep[]
  sources?: Array<Record<string, unknown>>
}

export type AgentRunListQuery = {
  status?: string
  page?: number
  pageSize?: number
}

export function pageAgentRuns(query: AgentRunListQuery = {}) {
  const q = new URLSearchParams()
  if (query.status) q.set('status', query.status)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 10))
  return apiGet<PageResult<AgentRun>>(`/api/v1/agent-runs/page?${q}`)
}

export function getAgentRun(id: string) {
  return apiGet<AgentRun>(`/api/v1/agent-runs/${encodeURIComponent(id)}`)
}

export function executeAgentRun(payload: { knowledgeBaseId: string; question: string }) {
  return apiPost<AgentRun>('/api/v1/agent-runs/execute', payload)
}

export function deleteAgentRun(id: string) {
  return apiDelete<null>(`/api/v1/agent-runs/${encodeURIComponent(id)}`)
}

export function batchDeleteAgentRuns(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/agent-runs/batch-delete', { ids })
}

export type QaApp = {
  id: string
  name: string
  description?: string
  knowledgeBaseId: string
  knowledgeBaseName?: string
  retrievalStrategyId: string
  retrievalStrategyName?: string
  chatModelId?: string
  answerPromptId?: string
  enableAgentic?: number
  historyLimit?: number
  fallbackReply?: string
  enabled?: number
  createTime?: string
  updateTime?: string
}

export type QaAppSavePayload = {
  name: string
  description?: string
  knowledgeBaseId: string
  retrievalStrategyId?: string
  chatModelId?: string
  answerPromptId?: string
  enableAgentic?: number
  historyLimit?: number
  fallbackReply?: string
  enabled?: number
}

export type QaAppListQuery = {
  name?: string
  enabled?: number
  page?: number
  pageSize?: number
}

export function pageQaApps(query: QaAppListQuery = {}) {
  const q = new URLSearchParams()
  if (query.name) q.set('name', query.name)
  if (query.enabled !== undefined) q.set('enabled', String(query.enabled))
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 10))
  return apiGet<PageResult<QaApp>>(`/api/v1/qa-apps/page?${q}`)
}

export function listQaApps() {
  return apiGet<QaApp[]>('/api/v1/qa-apps')
}

export function getQaApp(id: string) {
  return apiGet<QaApp>(`/api/v1/qa-apps/${encodeURIComponent(id)}`)
}

export function createQaApp(payload: QaAppSavePayload) {
  return apiPost<QaApp>('/api/v1/qa-apps', payload)
}

export function updateQaApp(id: string, payload: QaAppSavePayload) {
  return apiPut<QaApp>(`/api/v1/qa-apps/${encodeURIComponent(id)}`, payload)
}

export function deleteQaApp(id: string) {
  return apiDelete<null>(`/api/v1/qa-apps/${encodeURIComponent(id)}`)
}

export function batchDeleteQaApps(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/qa-apps/batch-delete', { ids })
}

export type ChatSession = {
  id: string
  appId: string
  title?: string
  createTime?: string
}

export type ChatMessage = {
  id: string
  sessionId?: string
  role: 'USER' | 'ASSISTANT' | string
  content: string
  agentRunId?: string
  feedback?: string
  createTime?: string
  sources?: Array<Record<string, unknown>>
  conclusionLabel?: string
  stepCount?: number
  retrievalRounds?: number
  rewriteRounds?: number
}

export function createChatSession(appId: string) {
  return apiPost<ChatSession>('/api/v1/chat/sessions', { appId, clientSource: 'ADMIN' })
}

export function listChatSessions(appId: string) {
  return apiGet<ChatSession[]>(`/api/v1/chat/sessions?appId=${encodeURIComponent(appId)}`)
}

export function listChatMessages(sessionId: string) {
  return apiGet<ChatMessage[]>(`/api/v1/chat/sessions/${encodeURIComponent(sessionId)}/messages`)
}

export function feedbackChatMessage(messageId: string, feedback: 'LIKE' | 'DISLIKE' | '') {
  return apiPut<null>(`/api/v1/chat/messages/${encodeURIComponent(messageId)}/feedback`, {
    feedback: feedback || null,
  })
}

export type ChatStreamHandlers = {
  onSession?: (payload: { sessionId: string; appId: string }) => void
  onStatus?: (text: string) => void
  onSources?: (sources: Array<Record<string, unknown>>) => void
  onDelta?: (text: string) => void
  onFinal?: (payload: Record<string, unknown>) => void
  onMeta?: (payload: Record<string, unknown>) => void
  onError?: (message: string) => void
}

/** 问答测试 SSE：经 Gateway → Java，直线链路 / Agentic 统一入口 */
export async function chatAppStream(
  payload: { appId: string; sessionId?: string; question: string },
  handlers: ChatStreamHandlers,
  opts?: { signal?: AbortSignal },
) {
  const token = getAdminToken()
  const res = await fetch('/api/v1/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ ...payload, clientSource: 'ADMIN' }),
    signal: opts?.signal,
  })
  if (!res.ok || !res.body) {
    if (res.status === 401 || res.status === 403) {
      throwUnauthorized()
    }
    const text = await res.text()
    throw new Error(text || `chat stream failed ${res.status}`)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  try {
    while (true) {
      if (opts?.signal?.aborted) {
        await reader.cancel()
        break
      }
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const chunks = buffer.split('\n\n')
      buffer = chunks.pop() || ''
      for (const chunk of chunks) {
        const lines = chunk.split('\n')
        let event = 'message'
        const dataLines: string[] = []
        for (const line of lines) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
        }
        if (!dataLines.length) continue
        const raw = dataLines.join('\n')
        let data: any = raw
        try {
          data = JSON.parse(raw)
        } catch {
          /* keep string */
        }
        if (event === 'session') handlers.onSession?.(data)
        else if (event === 'meta') handlers.onMeta?.(data)
        else if (event === 'status') handlers.onStatus?.(data?.text || String(data))
        else if (event === 'sources') handlers.onSources?.(data?.sources || [])
        else if (event === 'delta') handlers.onDelta?.(data?.content || '')
        else if (event === 'final') handlers.onFinal?.(data)
        else if (event === 'error') handlers.onError?.(data?.message || raw)
      }
    }
  } catch (e) {
    if (opts?.signal?.aborted || (e instanceof DOMException && e.name === 'AbortError')) {
      return
    }
    throw e
  }
}

export type ChatFeedbackStat = {
  likeCount: number
  dislikeCount: number
  satisfactionRate: number
}

export type ChatSessionLog = {
  id: string
  appId?: string
  appName?: string
  title?: string
  messageCount?: number
  clientSource?: string
  likeCount?: number
  dislikeCount?: number
  createTime?: string
}

export type ChatFeedbackItem = {
  id: string
  sessionId?: string
  feedback?: string
  question?: string
  answer?: string
  feedbackReason?: string
  settled?: number
  createTime?: string
  appName?: string
}

export function fetchChatLogStats() {
  return apiGet<ChatFeedbackStat>('/api/v1/chat-logs/stats')
}

export function pageChatSessions(query: { title?: string; page?: number; pageSize?: number } = {}) {
  const q = new URLSearchParams()
  if (query.title) q.set('title', query.title)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 10))
  return apiGet<PageResult<ChatSessionLog>>(`/api/v1/chat-logs/sessions/page?${q}`)
}

export function getChatSessionMessages(sessionId: string) {
  return apiGet<ChatMessage[]>(
    `/api/v1/chat-logs/sessions/${encodeURIComponent(sessionId)}/messages`,
  )
}

export function pageChatFeedback(query: {
  feedback?: string
  settled?: number
  page?: number
  pageSize?: number
} = {}) {
  const q = new URLSearchParams()
  if (query.feedback) q.set('feedback', query.feedback)
  if (query.settled !== undefined) q.set('settled', String(query.settled))
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 10))
  return apiGet<PageResult<ChatFeedbackItem>>(`/api/v1/chat-logs/feedback/page?${q}`)
}

export function updateChatFeedbackSettled(id: string, settled: number) {
  return apiPut<null>(`/api/v1/chat-logs/feedback/${encodeURIComponent(id)}/settled`, { settled })
}

export type EvalDataset = {
  id: string
  name: string
  description?: string
  knowledgeBaseId?: string
  knowledgeBaseName?: string
  sourceType?: string
  caseCount?: number
  createTime?: string
}

export type EvalCase = {
  id: string
  datasetId: string
  question: string
  expectedAnswer?: string
  sourceType?: string
  includeInEval?: number
  remark?: string
  knowledgeBaseId?: string
  documentId?: string
  sourceSegmentIds?: string
  sourceContent?: string
  sourceLabel?: string
  createTime?: string
}

export type EvalDatasetSavePayload = {
  name: string
  description?: string
  knowledgeBaseId?: string
  sourceType?: string
}

export type EvalCaseSavePayload = {
  question: string
  expectedAnswer?: string
  includeInEval?: number
  remark?: string
  sourceType?: string
}

export function listEvalDatasets() {
  return apiGet<EvalDataset[]>('/api/v1/eval/datasets')
}

export function createEvalDataset(payload: EvalDatasetSavePayload) {
  return apiPost<EvalDataset>('/api/v1/eval/datasets', payload)
}

export function deleteEvalDataset(id: string) {
  return apiDelete<null>(`/api/v1/eval/datasets/${encodeURIComponent(id)}`)
}

export function batchDeleteEvalDatasets(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/eval/datasets/batch-delete', { ids })
}

export function pageEvalCases(
  datasetId: string,
  query: { sourceType?: string; question?: string; page?: number; pageSize?: number } = {},
) {
  const q = new URLSearchParams()
  if (query.sourceType) q.set('sourceType', query.sourceType)
  if (query.question) q.set('question', query.question)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 10))
  return apiGet<PageResult<EvalCase>>(
    `/api/v1/eval/datasets/${encodeURIComponent(datasetId)}/cases/page?${q}`,
  )
}

export function createEvalCase(datasetId: string, payload: EvalCaseSavePayload) {
  return apiPost<EvalCase>(`/api/v1/eval/datasets/${encodeURIComponent(datasetId)}/cases`, payload)
}

export function updateEvalCase(id: string, payload: EvalCaseSavePayload) {
  return apiPut<EvalCase>(`/api/v1/eval/cases/${encodeURIComponent(id)}`, payload)
}

export function deleteEvalCase(id: string) {
  return apiDelete<null>(`/api/v1/eval/cases/${encodeURIComponent(id)}`)
}

export function batchDeleteEvalCases(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/eval/cases/batch-delete', { ids })
}

export function getEvalCase(id: string) {
  return apiGet<EvalCase>(`/api/v1/eval/cases/${encodeURIComponent(id)}`)
}

export function generateEvalCasesFromDoc(
  datasetId: string,
  payload: { knowledgeBaseId?: string; documentId: string; count?: number },
) {
  return apiPost<{ created: number }>(
    `/api/v1/eval/datasets/${encodeURIComponent(datasetId)}/generate-from-doc`,
    payload,
  )
}

export function importEvalCasesFromDislikes(datasetId: string) {
  return apiPost<{ created: number; message?: string }>(
    `/api/v1/eval/datasets/${encodeURIComponent(datasetId)}/import-dislikes`,
    {},
  )
}

export function countPendingDislikes() {
  return apiGet<{ count: number }>('/api/v1/eval/pending-dislikes/count')
}

export type EvalReport = {
  id: string
  taskNo?: number
  datasetId: string
  datasetName?: string
  retrievalStrategyId?: string
  strategyName?: string
  status: string
  caseCount?: number
  doneCount?: number
  contextRecall?: number
  contextPrecision?: number
  faithfulness?: number
  answerRelevancy?: number
  overallScore?: number
  failReason?: string
  durationMs?: number
  avgLatencyMs?: number
  capabilityTags?: string[]
  createTime?: string
  cases?: EvalReportCase[]
}

export type EvalReportCase = {
  caseId?: string
  question?: string
  expectedAnswer?: string
  generatedAnswer?: string
  contextRecall?: number
  contextPrecision?: number
  faithfulness?: number
  answerRelevancy?: number
  avgScore?: number
  failReason?: string | null
}

export function pageEvalReports(
  query: { datasetId?: string; status?: string; page?: number; pageSize?: number } = {},
) {
  const q = new URLSearchParams()
  if (query.datasetId) q.set('datasetId', query.datasetId)
  if (query.status) q.set('status', query.status)
  q.set('page', String(query.page ?? 1))
  q.set('pageSize', String(query.pageSize ?? 10))
  return apiGet<PageResult<EvalReport>>(`/api/v1/eval/reports/page?${q}`)
}

export function getEvalReport(id: string) {
  return apiGet<EvalReport>(`/api/v1/eval/reports/${encodeURIComponent(id)}`)
}

export function startEvalBatch(payload: { datasetId: string; retrievalStrategyId?: string }) {
  return apiPost<EvalReport>('/api/v1/eval/reports/start', payload)
}

export function deleteEvalReport(id: string) {
  return apiDelete<null>(`/api/v1/eval/reports/${encodeURIComponent(id)}`)
}

export function batchDeleteEvalReports(ids: string[]) {
  return apiPost<{ deleted: number }>('/api/v1/eval/reports/batch-delete', { ids })
}

export type LoginData = {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: { id: string; username: string; nickname: string; role: string; avatarUrl?: string }
}

export function login(username: string, password: string) {
  return apiPost<LoginData>('/api/v1/auth/login', { username, password })
}

export type UserProfile = {
  id: string
  username: string
  nickname?: string
  avatarUrl?: string
  role?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export function fetchMyProfile() {
  return apiGet<UserProfile>('/api/v1/auth/me')
}

export function updateMyProfile(payload: { nickname?: string; avatarUrl?: string }) {
  return apiPut<UserProfile>('/api/v1/auth/profile', payload)
}

export function changeMyPassword(payload: { oldPassword: string; newPassword: string }) {
  return apiPut<null>('/api/v1/auth/password', payload)
}

export type DashboardNamedCount = {
  id?: string
  name: string
  extra?: string
  count: number
}

export type DashboardHotQuestion = {
  question: string
  count: number
  lastAskTime?: string
}

export type DashboardDailyUsage = {
  date: string
  questions: number
  agentRuns: number
  toolCalls: number
  chatCalls: number
}

export type DashboardOverview = {
  days: number
  summary: {
    knowledgeBaseCount: number
    documentCount: number
    questionCount: number
    agentRunCount: number
    toolCallCount: number
    chatSessionCount: number
  }
  hotKnowledgeBases: DashboardNamedCount[]
  hotQuestions: DashboardHotQuestion[]
  usageTrend: DashboardDailyUsage[]
  modelCalls: DashboardNamedCount[]
  modelTypeCalls: DashboardNamedCount[]
}

export function fetchDashboardOverview(days = 14) {
  return apiGet<DashboardOverview>(`/api/v1/dashboard/overview?days=${days}`)
}

export type SystemConfigItem = {
  id: string
  group: string
  key: string
  value: string
  valueType: string
  label: string
  description?: string
  sortOrder?: number
  editable?: number
  updateTime?: string
}

export function listSystemConfigs() {
  return apiGet<SystemConfigItem[]>('/api/v1/system/configs')
}

export function saveSystemConfigs(items: { key: string; value: string }[]) {
  return apiPut<SystemConfigItem[]>('/api/v1/system/configs', { items })
}

export function reloadSystemConfigs() {
  return apiPost<{ size: number }>('/api/v1/system/configs/reload', {})
}

export function classifyIntent(question: string) {
  return apiPost<{
    question?: string
    intentLevel?: string
    intentConfidence?: number
    intentReason?: string
    pipelineHint?: string
  }>('/api/v1/ai/intent/classify', { question })
}

export function lookupSemanticCache(payload: {
  question: string
  appId?: string
  knowledgeBaseId?: string
}) {
  return apiPost<{
    hit: boolean
    similarity?: number
    mode?: string
    matchedQuestion?: string
    answer?: string
    sources?: Array<Record<string, unknown>>
    stats?: Record<string, unknown>
  }>('/api/v1/ai/cache/lookup', payload)
}

export function storeSemanticCache(payload: {
  question: string
  answer: string
  appId?: string
  knowledgeBaseId?: string
}) {
  return apiPost<{ ok: boolean; stats?: Record<string, unknown> }>('/api/v1/ai/cache/store', payload)
}

export function clearSemanticCache(payload: { appId?: string; knowledgeBaseId?: string } = {}) {
  return apiPost<{ deleted: number; stats?: Record<string, unknown> }>('/api/v1/ai/cache/clear', payload)
}

export function fetchSemanticCacheStats() {
  return apiGet<Record<string, unknown>>('/api/v1/ai/cache/stats')
}

export function fetchKgOverview() {
  return apiGet<Record<string, unknown>>('/api/v1/knowledge/kg/overview')
}

export function searchKg(q: string, limit = 10) {
  const qs = new URLSearchParams()
  if (q) qs.set('q', q)
  qs.set('limit', String(limit))
  return apiGet<Array<Record<string, unknown>>>(`/api/v1/knowledge/kg/search?${qs}`)
}

export function multiHopKg(entityId: string, depth = 2) {
  return apiPost<{ entityId: string; depth: number; paths: Array<Record<string, unknown>> }>(
    '/api/v1/knowledge/kg/multi-hop',
    { entityId, depth },
  )
}
