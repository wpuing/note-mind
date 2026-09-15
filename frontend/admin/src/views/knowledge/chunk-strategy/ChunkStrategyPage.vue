<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { getAdminToken } from '@/api/ai'
import {
  batchDeleteChunkStrategies,
  createChunkStrategy,
  deleteChunkStrategy,
  pageChunkStrategies,
  updateChunkStrategy,
  type ChunkStrategy,
} from '@/api/http'
import { AppModal, AppPagination } from '@/components/common'

const loading = ref(false)
const saving = ref(false)
const rows = ref<ChunkStrategy[]>([])
const total = ref(0)
const selectedIds = ref<string[]>([])
const batchDeleting = ref(false)

const query = reactive({
  name: '',
  strategyType: '' as string,
  page: 1,
  pageSize: 8,
})

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const form = reactive({
  name: '',
  strategyType: 'RECURSIVE',
  chunkSize: 500,
  chunkOverlap: 50,
  parentChunkSize: 1000,
  childChunkSize: 300,
  childOverlap: 30,
  separatorsText: '\\n\\n,\\n,。,！,？,；, ,',
  enabled: 1 as number,
  isDefault: 0 as number,
  remark: '',
})

const TYPE_OPTIONS = [
  { value: 'RECURSIVE', label: '递归分块' },
  { value: 'PARENT_CHILD', label: '父子分块' },
  { value: 'FIXED', label: '固定长度' },
]

const isParentChild = computed(() => form.strategyType === 'PARENT_CHILD')

function ensureJavaJwt() {
  const token = getAdminToken() || ''
  if (!token || token.startsWith('local-')) {
    ElMessage.warning('请先登录获取 JWT（Gateway :8080 + Service :8081）')
    return false
  }
  return true
}

function typeLabel(t?: string) {
  return TYPE_OPTIONS.find((o) => o.value === t)?.label || t || '—'
}

function separatorsDisplay(row: ChunkStrategy) {
  const seps = row.separators
  if (!seps || !seps.length) return '—'
  return seps
    .map((s) => {
      if (s === '\n\n') return '\\n\\n'
      if (s === '\n') return '\\n'
      if (s === ' ') return '␠'
      if (s === '') return '(空)'
      return s
    })
    .join(', ')
}

function parseSeparators(text: string): string[] {
  return text
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
    .map((s) => s.replace(/\\n/g, '\n'))
}

function formatSeparators(list?: string[]): string {
  if (!list || !list.length) return '\\n\\n,\\n,。,！,？,；, ,'
  return list.map((s) => s.replace(/\n/g, '\\n')).join(',')
}

async function load() {
  if (!ensureJavaJwt()) return
  loading.value = true
  try {
    const page = await pageChunkStrategies({
      name: query.name.trim() || undefined,
      strategyType: query.strategyType || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    rows.value = page.records || []
    total.value = page.total ?? 0
    selectedIds.value = []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败（需先 Java 登录）')
  } finally {
    loading.value = false
  }
}

function onSelectionChange(selection: ChunkStrategy[]) {
  selectedIds.value = selection.map((r) => r.id).filter(Boolean)
}

function search() {
  query.page = 1
  void load()
}

function resetQuery() {
  query.name = ''
  query.strategyType = ''
  query.page = 1
  void load()
}

function onPageChange(payload: { page: number; pageSize: number }) {
  query.page = payload.page
  query.pageSize = payload.pageSize
  void load()
}

function openCreate() {
  if (!ensureJavaJwt()) return
  formMode.value = 'create'
  editingId.value = ''
  form.name = ''
  form.strategyType = 'RECURSIVE'
  form.chunkSize = 500
  form.chunkOverlap = 50
  form.parentChunkSize = 1000
  form.childChunkSize = 300
  form.childOverlap = 30
  form.separatorsText = '\\n\\n,\\n,。,！,？,；, ,'
  form.enabled = 1
  form.isDefault = 0
  form.remark = ''
  formOpen.value = true
}

function openEdit(row: ChunkStrategy) {
  if (!ensureJavaJwt()) return
  formMode.value = 'edit'
  editingId.value = row.id
  form.name = row.name
  form.strategyType = row.strategyType || 'RECURSIVE'
  form.chunkSize = row.chunkSize ?? 500
  form.chunkOverlap = row.chunkOverlap ?? 50
  form.parentChunkSize = row.parentChunkSize ?? 1000
  form.childChunkSize = row.childChunkSize ?? 300
  form.childOverlap = row.childOverlap ?? 30
  form.separatorsText = formatSeparators(row.separators)
  form.enabled = row.enabled ?? 1
  form.isDefault = row.isDefault ?? 0
  form.remark = row.remark || ''
  formOpen.value = true
}

async function submitForm() {
  if (!ensureJavaJwt()) return
  if (!form.name.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  if (!form.strategyType) {
    ElMessage.warning('请选择类型')
    return
  }
  if (form.chunkOverlap >= form.chunkSize) {
    ElMessage.warning('重叠长度必须小于切分长度')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      strategyType: form.strategyType,
      chunkSize: form.chunkSize,
      chunkOverlap: form.chunkOverlap,
      parentChunkSize: isParentChild.value ? form.parentChunkSize : undefined,
      childChunkSize: isParentChild.value ? form.childChunkSize : undefined,
      childOverlap: isParentChild.value ? form.childOverlap : undefined,
      separators: parseSeparators(form.separatorsText),
      enabled: form.enabled,
      isDefault: form.isDefault,
      remark: form.remark.trim() || undefined,
    }
    if (formMode.value === 'create') {
      await createChunkStrategy(payload)
      ElMessage.success('已创建切分策略')
    } else {
      await updateChunkStrategy(editingId.value, payload)
      ElMessage.success('已保存')
    }
    formOpen.value = false
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function setAsDefault(row: ChunkStrategy) {
  if (!ensureJavaJwt()) return
  if (row.isDefault === 1) return
  try {
    await updateChunkStrategy(row.id, {
      name: row.name,
      strategyType: row.strategyType,
      chunkSize: row.chunkSize,
      chunkOverlap: row.chunkOverlap,
      parentChunkSize: row.parentChunkSize,
      childChunkSize: row.childChunkSize,
      childOverlap: row.childOverlap,
      separators: row.separators,
      enabled: row.enabled ?? 1,
      isDefault: 1,
      remark: row.remark,
    })
    ElMessage.success(`已设「${row.name}」为默认`)
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '设默认失败')
  }
}

async function removeOne(row: ChunkStrategy) {
  if (!ensureJavaJwt()) return
  try {
    await ElMessageBox.confirm(`确认删除切分策略「${row.name}」？`, '删除确认', {
      type: 'warning',
    })
    await deleteChunkStrategy(row.id)
    ElMessage.success('已删除')
    void load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function removeBatch() {
  if (!ensureJavaJwt()) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选策略')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${selectedIds.value.length} 条切分策略？默认策略与被知识库引用的策略会跳过。`,
      '批量删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  batchDeleting.value = true
  try {
    const res = await batchDeleteChunkStrategies(selectedIds.value)
    ElMessage.success(`已删除 ${res.deleted} 条`)
    void load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="panel">
    <header class="panel-head">
      <div>
        <h2>切分策略</h2>
        <p>递归分块 / 父子分块；上传与知识库可绑定策略，仅子块入向量。</p>
      </div>
      <div class="head-actions">
        <el-button round :loading="loading" @click="load">刷新</el-button>
        <el-button
          type="danger"
          plain
          round
          :icon="Delete"
          :loading="batchDeleting"
          :disabled="!selectedIds.length"
          @click="removeBatch"
        >
          批量删除
        </el-button>
        <el-button type="primary" round :icon="Plus" @click="openCreate">新增</el-button>
      </div>
    </header>
    <div class="panel-body">
      <div class="filters">
        <el-input
          v-model="query.name"
          clearable
          placeholder="策略名称"
          style="width: 200px"
          @keyup.enter="search"
        />
        <el-select
          v-model="query.strategyType"
          clearable
          placeholder="全部类型"
          style="width: 160px"
        >
          <el-option
            v-for="o in TYPE_OPTIONS"
            :key="o.value"
            :label="o.label"
            :value="o.value"
          />
        </el-select>
        <el-button type="primary" round @click="search">查询</el-button>
        <el-button round @click="resetQuery">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe @selection-change="onSelectionChange">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="策略名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="策略类型" width="110">
          <template #default="{ row }">{{ typeLabel(row.strategyType) }}</template>
        </el-table-column>
        <el-table-column prop="chunkSize" label="片段长度" width="96" align="center" />
        <el-table-column prop="chunkOverlap" label="重叠字符" width="96" align="center" />
        <el-table-column label="父块长度" width="96" align="center">
          <template #default="{ row }">
            {{ row.parentChunkSize != null ? row.parentChunkSize : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="分隔符" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ separatorsDisplay(row) }}</template>
        </el-table-column>
        <el-table-column label="默认" width="88" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" size="small" type="success" effect="light" round>
              默认
            </el-tag>
            <button
              v-else
              type="button"
              class="default-no-btn"
              :disabled="row.enabled === 0"
              title="点击设为默认"
              @click="setAsDefault(row)"
            >
              否
            </button>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="108" fixed="right" align="center">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button type="primary" size="small" circle :icon="Edit" @click="openEdit(row)" />
              <el-button type="danger" size="small" circle :icon="Delete" @click="removeOne(row)" />
            </div>
          </template>
        </el-table-column>
      </el-table>

      <AppPagination
        :total="total"
        :page="query.page"
        :page-size="query.pageSize"
        @change="onPageChange"
      />
    </div>
  </section>

  <AppModal
    v-model="formOpen"
    :title="formMode === 'create' ? '新增切分策略' : '编辑切分策略'"
    variant="form"
    width="720px"
    :close-on-click-modal="!saving"
  >
    <div class="app-modal-body">
      <div class="app-modal-step">
        <div class="app-modal-step-title">
          <span class="app-modal-step-no">1</span>
          <label>基本信息 <em>*</em></label>
        </div>
        <div class="form-grid">
          <label>策略名称</label>
          <el-input v-model="form.name" maxlength="64" placeholder="策略名称" />
          <label>策略类型</label>
          <el-select
            v-model="form.strategyType"
            style="width: 100%"
            popper-class="app-modal-select-popper"
          >
            <el-option
              v-for="o in TYPE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
          <label>片段长度</label>
          <el-input-number v-model="form.chunkSize" :min="50" :max="8000" :step="50" />
          <label>重叠字符</label>
          <el-input-number v-model="form.chunkOverlap" :min="0" :max="2000" :step="10" />
          <template v-if="isParentChild">
            <label>父块长度</label>
            <el-input-number v-model="form.parentChunkSize" :min="100" :max="16000" :step="50" />
            <label>子块长度</label>
            <el-input-number v-model="form.childChunkSize" :min="50" :max="4000" :step="50" />
            <label>子块重叠</label>
            <el-input-number v-model="form.childOverlap" :min="0" :max="1000" :step="10" />
          </template>
          <label>分隔符</label>
          <el-input
            v-model="form.separatorsText"
            type="textarea"
            :rows="2"
            placeholder="逗号分隔；换行写 \\n"
          />
          <label>备注</label>
          <el-input v-model="form.remark" maxlength="256" placeholder="可选" />
          <label>启用</label>
          <el-radio-group v-model="form.enabled">
            <el-radio :value="1">是</el-radio>
            <el-radio :value="0">否</el-radio>
          </el-radio-group>
          <label>默认</label>
          <el-radio-group v-model="form.isDefault">
            <el-radio :value="1">是</el-radio>
            <el-radio :value="0">否</el-radio>
          </el-radio-group>
        </div>
        <p class="app-modal-hint">
          父子分块仅子块写入向量库；设为默认时会取消其它策略的默认标记。
        </p>
      </div>
    </div>
    <template #footer>
      <el-button round :disabled="saving" @click="formOpen = false">取消</el-button>
      <el-button type="primary" round :loading="saving" @click="submitForm">
        {{ formMode === 'create' ? '创建' : '保存' }}
      </el-button>
    </template>
  </AppModal>
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
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(120deg, rgba(127, 173, 108, 0.14), transparent 55%);
}
.panel-head h2 { margin: 0 0 6px; font-size: 22px; }
.panel-head p { margin: 0; color: var(--ink-muted); font-size: 13px; }
.head-actions { display: flex; gap: 8px; align-items: flex-start; }
.panel-body { padding: 16px 20px 24px; display: grid; gap: 12px; }
.filters { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.row-actions {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
}
.default-no-btn {
  border: none;
  background: transparent;
  color: var(--ink-muted);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 6px;
}
.default-no-btn:hover:not(:disabled) {
  color: var(--matcha-700, #3d6b2e);
  background: rgba(75, 116, 61, 0.08);
}
.default-no-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.form-grid {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 12px 14px;
  align-items: center;
}
.form-grid label { color: var(--ink-muted); font-size: 13px; }
</style>
