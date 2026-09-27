<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchKgOverview, multiHopKg, searchKg } from '@/api/http'

const q = ref('测试机')
const loading = ref(false)
const overview = ref<Record<string, unknown> | null>(null)
const entities = ref<Array<Record<string, unknown>>>([])
const paths = ref<Array<Record<string, unknown>>>([])
const selectedId = ref('')

async function loadOverview() {
  overview.value = (await fetchKgOverview()) as Record<string, unknown>
}

async function doSearch() {
  loading.value = true
  try {
    entities.value = (await searchKg(q.value.trim(), 20)) || []
    if (entities.value.length && !selectedId.value) {
      selectedId.value = String(entities.value[0].id || '')
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '检索失败')
  } finally {
    loading.value = false
  }
}

async function doHop() {
  if (!selectedId.value) {
    ElMessage.warning('请先选中实体')
    return
  }
  loading.value = true
  try {
    const r = await multiHopKg(selectedId.value, 2)
    paths.value = (r.paths as Array<Record<string, unknown>>) || []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '多跳失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    await loadOverview()
    await doSearch()
  } catch {
    /* ignore */
  }
})
</script>

<template>
  <div class="page">
    <header class="head">
      <h2>图谱检索测试</h2>
      <p>Phase C 最小可测：本地 JSON 图谱（不依赖 Neo4j），支持实体检索与多跳</p>
    </header>
    <el-alert v-if="overview" type="info" :closable="false" show-icon class="mb">
      引擎 {{ overview.engine }} · 实体 {{ overview.entityCount }} · 关系 {{ overview.relationCount }}
    </el-alert>
    <el-card shadow="never">
      <div class="row">
        <el-input v-model="q" placeholder="实体关键词，如 X2 / 测试机" @keyup.enter="doSearch" />
        <el-button type="primary" :loading="loading" @click="doSearch">检索</el-button>
        <el-button :loading="loading" @click="doHop">多跳(depth=2)</el-button>
      </div>
      <el-table
        :data="entities"
        size="small"
        class="mt"
        highlight-current-row
        @current-change="(row: any) => (selectedId = row?.id || '')"
      >
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="description" label="描述" min-width="240" />
        <el-table-column prop="id" label="ID" width="160" />
      </el-table>
      <h3 class="sub">多跳路径</h3>
      <el-empty v-if="!paths.length" description="选中实体后点多跳" />
      <div v-for="(p, i) in paths" :key="i" class="path">
        <template v-for="(hop, j) in (p.hops as any[]) || []" :key="j">
          <span>{{ hop.fromName }}</span>
          <el-tag size="small" class="tag">{{ hop.type }}</el-tag>
          <span>{{ hop.toName }}</span>
          <span v-if="j < ((p.hops as any[]) || []).length - 1"> → </span>
        </template>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page { padding: 16px 20px 40px; }
.head h2 { margin: 0 0 4px; font-size: 18px; }
.head p { margin: 0 0 12px; color: #64748b; font-size: 13px; }
.row { display: flex; gap: 8px; }
.mt { margin-top: 12px; }
.mb { margin-bottom: 12px; }
.sub { margin: 16px 0 8px; font-size: 14px; }
.path { padding: 8px 0; border-bottom: 1px solid #eef2f7; font-size: 13px; }
.tag { margin: 0 6px; }
</style>
