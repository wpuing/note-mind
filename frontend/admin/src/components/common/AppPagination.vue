<script setup lang="ts">
import { computed } from 'vue'
import { DEFAULT_PAGE_SIZES } from '@/composables/usePagination'

const props = withDefaults(
  defineProps<{
    total: number
    page: number
    pageSize: number
    pageSizes?: number[]
    /** compact：详情内嵌等场景，去掉 jumper */
    compact?: boolean
    layout?: string
    popperClass?: string
    teleported?: boolean
  }>(),
  {
    pageSizes: () => [...DEFAULT_PAGE_SIZES],
    compact: false,
    teleported: true,
  },
)

const emit = defineEmits<{
  'update:page': [page: number]
  'update:pageSize': [pageSize: number]
  change: [payload: { page: number; pageSize: number }]
}>()

const resolvedLayout = computed(
  () => props.layout || (props.compact ? 'total, sizes, prev, pager, next' : 'total, sizes, prev, pager, next, jumper'),
)

function onCurrentChange(p: number) {
  emit('update:page', p)
  emit('change', { page: p, pageSize: props.pageSize })
}

function onSizeChange(size: number) {
  emit('update:pageSize', size)
  emit('update:page', 1)
  emit('change', { page: 1, pageSize: size })
}
</script>

<template>
  <div class="app-pager" :class="{ 'is-compact': compact }">
    <el-pagination
      background
      :layout="resolvedLayout"
      :total="total"
      :current-page="page"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :popper-class="popperClass"
      :teleported="teleported"
      @current-change="onCurrentChange"
      @size-change="onSizeChange"
    />
  </div>
</template>

<style scoped>
.app-pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding-top: 4px;
}
.app-pager.is-compact {
  margin: 0;
  padding: 10px 28px 8px 4px;
  border-top: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.98);
  position: relative;
  z-index: 6;
}
</style>
