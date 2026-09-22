<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminToken } from '@/api/ai'
import {
  executeAgentRun,
  listKnowledgeBases,
  type AgentRun,
  type KnowledgeBase,
} from '@/api/http'
import { AppModal } from '@/components/common'

const loading = ref(false)
const kbLoading = ref(false)
const kbOptions = ref<KnowledgeBase[]>([])
const form = reactive({
  knowledgeBaseId: '',
  question: '',
})
const result = ref<AgentRun | null>(null)
const timelineOpen = ref(false)

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

const summaryLabel = computed(() => result.value?.conclusionLabel || '—')
const summaryOk = computed(() => (result.value?.status || '') === 'SUCCESS')

function sourceField(row: Record<string, unknown>, keys: string[]) {
  for (const k of keys) {
    const v = row[k]
    if (v != null && String(v) !== '') return String(v)
  }
  return '—'
}

function stepDotClass(node?: string) {
  if (node === 'retrieve') return 'dot-green'
  if (node === 'grade' || node === 'check') return 'dot-orange'
  return 'dot-blue'
}

async function loadKb() {
  kbLoading.value = true
  try {
    kbOptions.value = (await listKnowledgeBases()) || []
    if (!form.knowledgeBaseId && kbOptions.value.length) {
      form.knowledgeBaseId = kbOptions.value[0].id
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载知识库失败')
  } finally {
    kbLoading.value = false
  }
}

async function start() {
  if (!ensureJavaJwt()) return
  if (!form.knowledgeBaseId) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!form.question.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  loading.value = true
  result.value = null
  try {
    result.value = await executeAgentRun({
      knowledgeBaseId: form.knowledgeBaseId,
      question: form.question.trim(),
    })
    ElMessage.success('执行完成')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '执行失败')
  } finally {
    loading.value = false
  }
}

function openTimeline() {
  if (!result.value) return
  timelineOpen.value = true
}

onMounted(() => void loadKb())
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>Agent 执行</h2>
        <p>改写、检索、判断相关、生成、校验；用于测试 Agentic 问答链路，结果会写入运行记录。</p>
      </div>
      <router-link class="link-btn" to="/ai/agent-run">
        <el-button round>运行记录</el-button>
      </router-link>
    </header>

    <div class="panel-body">
      <div class="run-form">
        <div class="form-row">
          <label>知识库</label>
          <el-select
            v-model="form.knowledgeBaseId"
            filterable
            :loading="kbLoading"
            placeholder="请选择知识库"
            style="width: 100%; max-width: 480px"
          >
            <el-option
              v-for="kb in kbOptions"
              :key="kb.id"
              :label="`${kb.name}（片段 ${kb.segmentCount ?? 0} 条）`"
              :value="kb.id"
            />
          </el-select>
        </div>
        <div class="form-row">
          <label>问题</label>
          <el-input
            v-model="form.question"
            placeholder="输入一个问题，看 Agent 怎么一步步找答案"
            maxlength="1000"
          />
        </div>
        <div class="form-actions">
          <el-button type="primary" round :loading="loading" @click="start">开始执行</el-button>
          <span class="hint">改写、检索、判断相关、生成、校验，跑几轮由模型的判断决定</span>
        </div>
      </div>

      <div v-if="result" class="result-block">
        <div class="result-summary">
          <el-tag size="small" round effect="light" :type="summaryOk ? 'success' : 'danger'">
            {{ summaryLabel }}
          </el-tag>
          <span>共 {{ result.stepCount ?? 0 }} 步</span>
          <span>检索 {{ result.retrievalRounds ?? 0 }} 次</span>
          <span>改写重试 {{ result.rewriteRounds ?? 0 }} 次</span>
          <span>耗时 {{ result.totalLatencyMs ?? '—' }} ms</span>
          <el-button type="primary" size="small" round @click="openTimeline">查看执行时间线</el-button>
        </div>

        <div class="answer-box">
          <h3>回答</h3>
          <pre>{{ result.finalAnswer || '—' }}</pre>
        </div>

        <div class="sources-box">
          <h3>引用来源</h3>
          <el-table :data="result.sources || []" stripe empty-text="无引用">
            <el-table-column type="index" label="序号" width="70" />
            <el-table-column label="片段ID" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                {{ sourceField(row, ['segment_id', 'segmentId', 'id']) }}
              </template>
            </el-table-column>
            <el-table-column label="页码" width="80" align="center">
              <template #default="{ row }">
                {{ sourceField(row, ['page', 'page_no', 'pageNo']) }}
              </template>
            </el-table-column>
            <el-table-column label="分数" width="100" align="center">
              <template #default="{ row }">
                {{ sourceField(row, ['score', 'rerank_score', 'cosine']) }}
              </template>
            </el-table-column>
            <el-table-column label="片段内容" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">
                {{ sourceField(row, ['content', 'text']) }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <AppModal v-model="timelineOpen" title="执行时间线" variant="form" width="720px">
      <div class="app-modal-body" v-if="result">
        <div class="app-modal-step">
          <div class="timeline-head">
            <el-tag size="small" round effect="light" :type="summaryOk ? 'success' : 'danger'">
              {{ result.status === 'SUCCESS' ? '已完成' : '运行失败' }}
            </el-tag>
            <span>共 {{ result.stepCount ?? 0 }} 步</span>
            <span>检索 {{ result.retrievalRounds ?? 0 }} 次</span>
            <span>改写 {{ result.rewriteRounds ?? 0 }} 次</span>
            <span>耗时 {{ result.totalLatencyMs ?? '—' }} ms</span>
          </div>
          <ul class="timeline">
            <li v-for="(s, idx) in result.steps || []" :key="s.id || idx">
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
.link-btn {
  text-decoration: none;
}
.panel-body {
  padding: 16px 20px 24px;
  display: grid;
  gap: 16px;
}
.run-form {
  display: grid;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
}
.form-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}
.form-row label {
  text-align: right;
  font-size: 13px;
  color: var(--ink-soft);
}
.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding-left: 82px;
}
.hint {
  font-size: 12px;
  color: var(--ink-muted);
}
.result-block {
  display: grid;
  gap: 14px;
}
.result-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f7faf5;
  border: 1px solid var(--line);
  font-size: 13px;
}
.answer-box,
.sources-box {
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
}
.answer-box h3,
.sources-box h3 {
  margin: 0 0 10px;
  font-size: 15px;
}
.answer-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.7;
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
  background: #5b8def;
}
.dot-green {
  background: #67c23a;
}
.dot-orange {
  background: #e6a23c;
}
.dot-blue {
  background: #409eff;
}
.step-body h4 {
  margin: 0 0 6px;
  font-size: 14px;
}
.io {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--ink);
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
