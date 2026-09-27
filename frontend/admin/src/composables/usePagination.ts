import { computed, ref, type Ref } from 'vue'

export const DEFAULT_PAGE_SIZES = [8, 20, 50] as const
export const DEFAULT_PAGE_SIZE = 8

export type PaginationChange = {
  page: number
  pageSize: number
}

export type UsePaginationOptions = {
  page?: number
  pageSize?: number
  pageSizes?: number[]
}

/** 服务端分页状态（自行请求，配合 AppPagination） */
export function usePagination(options: UsePaginationOptions = {}) {
  const page = ref(options.page ?? 1)
  const pageSize = ref(options.pageSize ?? DEFAULT_PAGE_SIZE)
  const total = ref(0)
  const pageSizes = options.pageSizes ?? [...DEFAULT_PAGE_SIZES]

  function setTotal(n: number) {
    total.value = n
  }

  function resetPage() {
    page.value = 1
  }

  function onPageChange(p: number) {
    page.value = p
  }

  function onSizeChange(size: number) {
    pageSize.value = size
    page.value = 1
  }

  return {
    page,
    pageSize,
    total,
    pageSizes,
    setTotal,
    resetPage,
    onPageChange,
    onSizeChange,
  }
}

/** 客户端切片分页（全量接口暂无 PageResult 时使用） */
export function useClientPagination<T>(
  source: Ref<T[]> | (() => T[]),
  options: UsePaginationOptions = {},
) {
  const page = ref(options.page ?? 1)
  const pageSize = ref(options.pageSize ?? DEFAULT_PAGE_SIZE)
  const pageSizes = options.pageSizes ?? [...DEFAULT_PAGE_SIZES]

  const allRows = computed(() => (typeof source === 'function' ? source() : source.value))
  const total = computed(() => allRows.value.length)
  const pagedRows = computed(() => {
    const start = (page.value - 1) * pageSize.value
    return allRows.value.slice(start, start + pageSize.value)
  })

  function onPageChange(p: number) {
    page.value = p
  }

  function onSizeChange(size: number) {
    pageSize.value = size
    page.value = 1
  }

  function resetPage() {
    page.value = 1
  }

  return {
    page,
    pageSize,
    total,
    pageSizes,
    pagedRows,
    resetPage,
    onPageChange,
    onSizeChange,
  }
}
