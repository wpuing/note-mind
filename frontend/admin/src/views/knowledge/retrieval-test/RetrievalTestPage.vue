<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAdminToken } from '@/api/ai'
import {
  knowledgeRetrievalTest,
  listDocuments,
  listKnowledgeBases,
  listRetrievalStrategies,
  type KnowledgeBase,
  type KnowledgeDocument,
  type KnowledgeRetrievalTestResult,
  type RetrievalStrategy,
} from '@/api/http'

const route = useRoute()
const router = useRouter()

const question = ref('')
const loading = ref(false)
const loadingKb = ref(false)
const loadingDocs = ref(false)
const bases = ref<KnowledgeBase[]>([])
const strategies = ref<RetrievalStrategy[]>([])
const documents = ref<KnowledgeDocument[]>([])
const knowledgeBaseId = ref('')
/** 空字符串 = 使用知识库绑定 / 系统默认 */
const retrievalStrategyId = ref('')
const documentIds = ref<string[]>([])
const result = ref<KnowledgeRetrievalTestResult | null>(null)

const currentKb = computed(() => bases.value.find((b) => b.id === knowledgeBaseId.value))

const boundStrategyId = computed(() => {
  const sid = currentKb.value?.retrievalStrategyId
  if (sid) return sid
  return strategies.value.find((s) => s.isDefault === 1)?.id || ''
})

const effectiveStrategy = computed(() => {
  const sid = retrievalStrategyId.value || boundStrategyId.value
  if (!sid) return null
  return strategies.value.find((s) => s.id === sid) || null
})

const strategyHint = computed(() => {
  if (retrievalStrategyId.value) {
    const s = strategies.value.find((x) => x.id === retrievalStrategyId.value)
    return s ? `本次覆盖使用：${s.name}` : '已选策略'
  }
  const s = effectiveStrategy.value
  if (!s) return '将使用系统默认策略'
  return `跟随知识库绑定：${s.name}`
})

function kbOptionLabel(b: KnowledgeBase) {
  const segs = b.segmentCount ?? 0
  return `${b.name}（片段 ${segs} 条）`
}

function docOptionLabel(d: KnowledgeDocument) {
  const segs = d.segmentCount ?? 0
  const title = d.title || d.fileName || d.id
  return `${title}（片段 ${segs}）`
}

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT')
    return false
  }
  return true
}

async function loadDocs(kbId: string) {
  documents.value = []
  documentIds.value = []
  if (!kbId) return
  loadingDocs.value = true
  try {
    const pageSize = 200
    const page = await listDocuments({ knowledgeBaseId: kbId, page: 1, pageSize })
    documents.value = page?.records || []
    if ((page?.total ?? 0) > pageSize) {
      ElMessage.warning(`文档下拉仅展示前 ${pageSize} 条（共 ${page.total}），请缩小知识库或后续支持搜索`)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载文档失败')
  } finally {
    loadingDocs.value = false
  }
}

async function loadMeta() {
  if (!ensureJavaJwt()) return
  loadingKb.value = true
  try {
    const [kbList, rsList] = await Promise.all([listKnowledgeBases(), listRetrievalStrategies()])
    bases.value = Array.isArray(kbList) ? kbList : []
    strategies.value = Array.isArray(rsList) ? rsList : []

    const qKb = typeof route.query.knowledgeBaseId === 'string' ? route.query.knowledgeBaseId : ''
    const qDoc = typeof route.query.documentId === 'string' ? route.query.documentId : ''
    const qStrategy =
      typeof route.query.retrievalStrategyId === 'string' ? route.query.retrievalStrategyId : ''

    if (qKb && bases.value.some((b) => b.id === qKb)) {
      knowledgeBaseId.value = qKb
    } else if (!bases.value.find((b) => b.id === knowledgeBaseId.value) && bases.value[0]) {
      knowledgeBaseId.value = bases.value[0].id
    }

    if (qStrategy && strategies.value.some((s) => s.id === qStrategy)) {
      retrievalStrategyId.value = qStrategy
    }

    if (knowledgeBaseId.value) {
      await loadDocs(knowledgeBaseId.value)
      if (qDoc && documents.value.some((d) => d.id === qDoc)) {
        documentIds.value = [qDoc]
      }
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载知识库失败')
  } finally {
    loadingKb.value = false
  }
}

async function run() {
  if (!ensureJavaJwt()) return
  if (!knowledgeBaseId.value) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!question.value.trim()) {
    ElMessage.warning('请输入检索问题')
    return
  }
  loading.value = true
  result.value = null
  try {
    result.value = await knowledgeRetrievalTest({
      question: question.value.trim(),
      knowledgeBaseId: knowledgeBaseId.value,
      retrievalStrategyId: retrievalStrategyId.value || undefined,
      documentIds: documentIds.value.length ? [...documentIds.value] : undefined,
    })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '检索失败')
  } finally {
    loading.value = false
  }
}

function preview(item: Record<string, unknown>) {
  const text = String(item.content_preview || item.content || '')
  return text.slice(0, 320)
}

function hitDocId(item: Record<string, unknown>) {
  return String(item.document_id || item.documentId || '')
}

function hitDocTitle(item: Record<string, unknown>) {
  const id = hitDocId(item)
  const doc = documents.value.find((d) => d.id === id)
  if (doc) return doc.title || doc.fileName || id
  return String(item.document_title || item.title || id || '—')
}

function hitScore(item: Record<string, unknown>) {
  const v = item.score
  if (v == null || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toFixed(6)
}

function hitRecallSource(item: Record<string, unknown>) {
  return String(item.recall_source || item.recallSource || '—')
}

function hitSegmentType(item: Record<string, unknown>) {
  return String(item.segment_type || item.segmentType || '普通块')
}

function hitChildContent(item: Record<string, unknown>) {
  const t = String(item.hit_child_content || item.hitChildContent || '').trim()
  return t || '—'
}

function stageName(s: Record<string, unknown>) {
  return String(s.name || '—')
}

function stageCount(s: Record<string, unknown>) {
  return String(s.count_text ?? s.countText ?? '—')
}

function stageMs(s: Record<string, unknown>) {
  const v = s.elapsed_ms ?? s.elapsedMs
  return v == null ? '—' : String(v)
}

function stageDetail(s: Record<string, unknown>) {
  return String(s.detail || '')
}

function stageThresholdLabel(s: Record<string, unknown>) {
  return String(s.threshold_label || s.thresholdLabel || '')
}

function stageExplain(s: Record<string, unknown>) {
  return String(s.explain || '')
}

function stageNotApplicable(s: Record<string, unknown>) {
  return !!(s.not_applicable ?? s.notApplicable)
}

function goDocument(item: Record<string, unknown>) {
  const id = hitDocId(item)
  router.push({
    path: '/knowledge/document',
    query: {
      knowledgeBaseId: knowledgeBaseId.value || undefined,
      ...(id ? { highlight: id } : {}),
    },
  })
}

function goSegments(item: Record<string, unknown>) {
  const id = hitDocId(item)
  router.push({
    path: '/knowledge/segment',
    query: {
      knowledgeBaseId: knowledgeBaseId.value || undefined,
      documentId: id || undefined,
    },
  })
}

function goStrategyManage() {
  router.push('/knowledge/retrieval-strategy')
}

function goKbManage() {
  router.push('/knowledge/base')
}

function goDocManage() {
  router.push({
    path: '/knowledge/document',
    query: knowledgeBaseId.value ? { knowledgeBaseId: knowledgeBaseId.value } : undefined,
  })
}

const hits = computed(() => {
  const r = result.value
  if (!r) return []
  return (r.sources || r.segments || []) as Record<string, unknown>[]
})

const stages = computed(() => {
  const list = result.value?.stages || []
  return list as Record<string, unknown>[]
})

const scoreColumnLabel = computed(() => {
  return result.value?.scoreScaleLabel || result.value?.scoreScale || '分数'
})

const usedQueries = computed(() => {
  const r = result.value
  if (!r) return []
  if (r.retrievalQueries?.length) return r.retrievalQueries
  if (r.rewrittenQueries?.length) return r.rewrittenQueries
  return []
})

const rrfExplain = computed(() => {
  return stages.value.find((s) => stageNotApplicable(s) && stageExplain(s))
})

watch(knowledgeBaseId, async (id, prev) => {
  result.value = null
  if (id && id !== prev) {
    await loadDocs(id)
  }
})

onMounted(loadMeta)
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>检索测试</h2>
        <p>
          选择知识库与可选限定文档、检索策略后发起检索；不选策略则跟随知识库绑定，不选文档则整库检索。
        </p>
      </div>
      <div class="head-links">
        <el-button link type="primary" @click="goKbManage">知识库管理</el-button>
        <el-button link type="primary" @click="goDocManage">文件管理</el-button>
        <el-button link type="primary" @click="goStrategyManage">检索策略</el-button>
      </div>
    </header>

    <div class="panel-body">
      <el-form class="test-form" label-width="96px" @submit.prevent>
        <el-form-item label="知识库" required>
          <el-select
            v-model="knowledgeBaseId"
            filterable
            :loading="loadingKb"
            placeholder="请选择知识库"
            style="width: 100%"
          >
            <el-option
              v-for="b in bases"
              :key="b.id"
              :label="kbOptionLabel(b)"
              :value="b.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="限定文档">
          <el-select
            v-model="documentIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :loading="loadingDocs"
            :disabled="!knowledgeBaseId"
            placeholder="不选表示在整个知识库里检索"
            style="width: 100%"
          >
            <el-option
              v-for="d in documents"
              :key="d.id"
              :label="docOptionLabel(d)"
              :value="d.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="检索策略">
          <div class="strategy-field">
            <el-select
              v-model="retrievalStrategyId"
              filterable
              clearable
              placeholder="不选则使用知识库绑定 / 默认策略"
              style="width: 100%"
            >
              <el-option
                v-for="s in strategies"
                :key="s.id"
                :label="`${s.name}${s.isDefault === 1 ? '（默认）' : ''}`"
                :value="s.id"
              />
            </el-select>
            <p class="field-hint">{{ strategyHint }}</p>
          </div>
        </el-form-item>

        <el-form-item label="检索问题" required>
          <el-input
            v-model="question"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="输入一个用户可能会问的问题"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" round :loading="loading" @click="run">开始检索</el-button>
        </el-form-item>
      </el-form>

      <div v-if="result" class="result-block">
        <p class="result-summary">
          使用策略：<b>{{ result.strategyName || '—' }}</b>
          <span>召回 {{ result.hitCount ?? hits.length }} 条</span>
          <span>总耗时 {{ result.elapsedMs ?? '—' }} ms</span>
          <span>分数量纲：{{ scoreColumnLabel }}</span>
        </p>

        <div class="stage-wrap">
          <el-table :data="stages" size="small" border stripe empty-text="无阶段明细">
            <el-table-column label="阶段" min-width="180">
              <template #default="{ row }">
                <span>{{ stageName(row) }}</span>
                <el-tooltip
                  v-if="stageThresholdLabel(row)"
                  :content="String(row.threshold_hint || row.thresholdHint || row.detail || '')"
                  placement="top"
                >
                  <el-tag size="small" type="info" class="thr-tag" effect="plain">
                    {{ stageThresholdLabel(row) }}
                  </el-tag>
                </el-tooltip>
                <el-tag
                  v-else-if="stageNotApplicable(row)"
                  size="small"
                  type="info"
                  class="thr-tag"
                  effect="plain"
                >
                  本次不适用
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="条数" width="100" align="center">
              <template #default="{ row }">{{ stageCount(row) }}</template>
            </el-table-column>
            <el-table-column label="耗时 (ms)" width="100" align="right">
              <template #default="{ row }">{{ stageMs(row) }}</template>
            </el-table-column>
            <el-table-column label="明细" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ stageDetail(row) || '—' }}</template>
            </el-table-column>
          </el-table>
          <p v-if="rrfExplain" class="rrf-explain">{{ stageExplain(rrfExplain) }}</p>
        </div>

        <div v-if="usedQueries.length" class="query-pills">
          <div class="query-pills-title">实际用于检索的问题（共 {{ usedQueries.length }} 条）：</div>
          <div class="pill-list">
            <el-tag
              v-for="(q, i) in usedQueries"
              :key="i"
              type="primary"
              effect="plain"
              round
              class="q-pill"
            >
              {{ q }}
            </el-tag>
          </div>
        </div>

        <el-table
          :data="hits"
          size="small"
          border
          stripe
          empty-text="无命中结果"
          class="hit-table"
        >
          <el-table-column label="名次" width="64" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="召回来源" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ hitRecallSource(row) }}</template>
          </el-table-column>
          <el-table-column :label="scoreColumnLabel" width="120" align="right">
            <template #default="{ row }">
              <el-tooltip :content="hitScore(row)" placement="top" :show-after="200">
                <span class="score-cell">{{ hitScore(row) }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="来源文档" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <button type="button" class="linkish" @click="goDocument(row)">
                {{ hitDocTitle(row) }}
              </button>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="80" align="center">
            <template #default="{ row }">{{ hitSegmentType(row) }}</template>
          </el-table-column>
          <el-table-column label="命中子块" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ hitChildContent(row) }}</template>
          </el-table-column>
          <el-table-column label="片段内容" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <button type="button" class="snippet-btn" @click="goSegments(row)">
                {{ preview(row) }}
              </button>
            </template>
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
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
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
.head-links {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex-shrink: 0;
}
.panel-body {
  padding: 20px 24px 28px;
  display: grid;
  gap: 20px;
}
.test-form {
  max-width: 760px;
}
.strategy-field {
  width: 100%;
}
.field-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--ink-muted);
  line-height: 1.4;
}
.result-block {
  display: grid;
  gap: 14px;
}
.result-summary {
  margin: 0;
  font-size: 13px;
  color: var(--ink-soft);
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
}
.result-summary b {
  color: var(--ink);
  font-weight: 650;
}
.stage-wrap {
  display: grid;
  gap: 8px;
}
.thr-tag {
  margin-left: 8px;
  vertical-align: middle;
}
.rrf-explain {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f7faf5;
  border: 1px solid var(--line);
  color: var(--ink-muted);
  font-size: 12px;
  line-height: 1.5;
}
.query-pills-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.pill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.q-pill {
  max-width: 100%;
  height: auto;
  white-space: normal;
  line-height: 1.4;
  padding: 6px 12px;
}
.hit-table {
  width: 100%;
}
.score-cell {
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}
.linkish {
  border: none;
  background: transparent;
  padding: 0;
  color: var(--matcha-700, #3d6b2e);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
  text-align: left;
}
.snippet-btn {
  border: none;
  background: transparent;
  padding: 0;
  margin: 0;
  font: inherit;
  font-size: 12px;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  line-height: 1.45;
  white-space: pre-wrap;
}
.snippet-btn:hover {
  color: var(--matcha-700, #3d6b2e);
}
</style>
