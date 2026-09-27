<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { classifyIntent } from '@/api/http'

const question = ref('对比 X1 和 X2 的续航差异，测试机向谁申请？')
const loading = ref(false)
const result = ref<Record<string, unknown> | null>(null)
const batchText = ref('X2 续航是多少？\n你好\n对比 X1 和 X2 散热并说明申请流程')
const batchRows = ref<Array<Record<string, unknown>>>([])

async function runOne() {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  loading.value = true
  try {
    result.value = (await classifyIntent(question.value.trim())) as Record<string, unknown>
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '分类失败')
  } finally {
    loading.value = false
  }
}

async function runBatch() {
  const lines = batchText.value
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
  batchRows.value = []
  loading.value = true
  try {
    for (const line of lines) {
      const row = (await classifyIntent(line)) as Record<string, unknown>
      batchRows.value.push(row)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="head">
      <h2>意图路由测试</h2>
      <p>Phase B：L1 直线 / L2 Agentic / L3 拒答（规则分类，可测）</p>
    </header>
    <el-card shadow="never">
      <el-input v-model="question" type="textarea" :rows="3" placeholder="输入问题" />
      <div class="actions">
        <el-button type="primary" :loading="loading" @click="runOne">分类</el-button>
      </div>
      <el-descriptions v-if="result" :column="1" border size="small" class="mt">
        <el-descriptions-item label="级别">{{ result.intentLevel }}</el-descriptions-item>
        <el-descriptions-item label="置信度">{{ result.intentConfidence }}</el-descriptions-item>
        <el-descriptions-item label="原因">{{ result.intentReason }}</el-descriptions-item>
        <el-descriptions-item label="建议管线">{{ result.pipelineHint }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
    <el-card shadow="never" class="mt">
      <template #header>批量用例（每行一题）</template>
      <el-input v-model="batchText" type="textarea" :rows="5" />
      <div class="actions">
        <el-button :loading="loading" @click="runBatch">批量分类</el-button>
      </div>
      <el-table v-if="batchRows.length" :data="batchRows" size="small" class="mt">
        <el-table-column prop="question" label="问题" min-width="220" />
        <el-table-column prop="intentLevel" label="级别" width="140" />
        <el-table-column prop="pipelineHint" label="管线" width="100" />
        <el-table-column prop="intentReason" label="原因" min-width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page { padding: 16px 20px 40px; }
.head h2 { margin: 0 0 4px; font-size: 18px; }
.head p { margin: 0 0 12px; color: #64748b; font-size: 13px; }
.actions { margin-top: 12px; }
.mt { margin-top: 12px; }
</style>
