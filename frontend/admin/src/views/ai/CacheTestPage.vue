<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  clearSemanticCache,
  fetchSemanticCacheStats,
  lookupSemanticCache,
  storeSemanticCache,
} from '@/api/http'

const form = reactive({
  appId: 'app_default',
  knowledgeBaseId: 'kb_default',
  question: 'X2 续航是多少小时？',
  answer: 'X2 产品续航约为 18 小时。',
})
const loading = ref(false)
const result = ref<Record<string, unknown> | null>(null)
const stats = ref<Record<string, unknown> | null>(null)

async function refreshStats() {
  stats.value = (await fetchSemanticCacheStats()) as Record<string, unknown>
}

async function lookup() {
  loading.value = true
  try {
    result.value = (await lookupSemanticCache({
      question: form.question,
      appId: form.appId,
      knowledgeBaseId: form.knowledgeBaseId,
    })) as Record<string, unknown>
    await refreshStats()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '查询失败')
  } finally {
    loading.value = false
  }
}

async function store() {
  loading.value = true
  try {
    await storeSemanticCache({
      question: form.question,
      answer: form.answer,
      appId: form.appId,
      knowledgeBaseId: form.knowledgeBaseId,
    })
    ElMessage.success('已写入缓存')
    await lookup()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '写入失败'
    ElMessage.error(
      msg.includes('禁用') || msg.includes('MANUAL_CACHE')
        ? '手工写入已关闭：请到问答测试真实提问落缓存，或设置 NOTEMIND_ALLOW_MANUAL_CACHE_WRITE=true（仅 ADMIN）'
        : msg,
    )
  } finally {
    loading.value = false
  }
}

async function clearAll() {
  loading.value = true
  try {
    const r = await clearSemanticCache({
      appId: form.appId,
      knowledgeBaseId: form.knowledgeBaseId,
    })
    ElMessage.success(`已清理 ${r.deleted} 条`)
    result.value = null
    await refreshStats()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '清理失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshStats().catch(() => undefined)
})
</script>

<template>
  <div class="page">
    <header class="head">
      <h2>语义缓存测试</h2>
      <p>Phase B：问答链路自动落缓存；手工写入默认关闭（需 ADMIN + NOTEMIND_ALLOW_MANUAL_CACHE_WRITE）。清理须指定应用或知识库。</p>
    </header>
    <el-card shadow="never">
      <el-form label-width="110px">
        <el-form-item label="应用 ID"><el-input v-model="form.appId" /></el-form-item>
        <el-form-item label="知识库 ID"><el-input v-model="form.knowledgeBaseId" /></el-form-item>
        <el-form-item label="问题"><el-input v-model="form.question" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="答案（写入用）"><el-input v-model="form.answer" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div class="actions">
        <el-button type="primary" :loading="loading" @click="lookup">查询缓存</el-button>
        <el-button
          :loading="loading"
          :disabled="stats?.allowManualCacheWrite === false"
          @click="store"
        >写入缓存</el-button>
        <el-button type="danger" plain :loading="loading" @click="clearAll">清理</el-button>
      </div>
      <p v-if="stats" class="stats">stats: {{ stats }}</p>
      <el-descriptions v-if="result" :column="1" border size="small" class="mt">
        <el-descriptions-item label="命中">{{ result.hit }}</el-descriptions-item>
        <el-descriptions-item label="相似度">{{ result.similarity }}</el-descriptions-item>
        <el-descriptions-item label="模式">{{ result.mode }}</el-descriptions-item>
        <el-descriptions-item label="匹配问题">{{ result.matchedQuestion }}</el-descriptions-item>
        <el-descriptions-item label="答案">{{ result.answer }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped>
.page { padding: 16px 20px 40px; }
.head h2 { margin: 0 0 4px; font-size: 18px; }
.head p { margin: 0 0 12px; color: #64748b; font-size: 13px; }
.actions { display: flex; gap: 8px; }
.stats { margin-top: 12px; color: #64748b; font-size: 12px; }
.mt { margin-top: 12px; }
</style>
