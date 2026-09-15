import { onBeforeUnmount, ref } from 'vue'

export type ResizeDir = 'e' | 's' | 'se'

export type UseDialogResizeOptions = {
  defaultWidth?: number
  defaultHeight?: number
  minWidth?: number
  minHeight?: number
}

/** 弹窗右边 / 右下角拖拽缩放 */
export function useDialogResize(options: UseDialogResizeOptions = {}) {
  const minW = options.minWidth ?? 760
  const minH = options.minHeight ?? 520
  const defaultW = options.defaultWidth ?? 1100
  const defaultH = options.defaultHeight ?? 720

  const width = ref(defaultW)
  const height = ref(defaultH)
  const resizing = ref(false)

  let resizeDir: ResizeDir = 'se'
  let startX = 0
  let startY = 0
  let startW = 0
  let startH = 0

  function resetSize() {
    const bottomGap = 96
    width.value = Math.min(defaultW, Math.max(minW, window.innerWidth - 80))
    height.value = Math.min(defaultH, Math.max(minH, window.innerHeight - bottomGap))
  }

  function onMove(e: MouseEvent) {
    if (!resizing.value) return
    const dx = e.clientX - startX
    const dy = e.clientY - startY
    const bottomGap = 96
    if (resizeDir === 'e' || resizeDir === 'se') {
      width.value = Math.min(window.innerWidth - 48, Math.max(minW, startW + dx))
    }
    if (resizeDir === 's' || resizeDir === 'se') {
      height.value = Math.min(window.innerHeight - bottomGap, Math.max(minH, startH + dy))
    }
  }

  function onEnd() {
    resizing.value = false
    document.body.style.userSelect = ''
    document.body.style.cursor = ''
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onEnd)
  }

  function startResize(dir: ResizeDir, e: MouseEvent) {
    e.preventDefault()
    e.stopPropagation()
    resizing.value = true
    resizeDir = dir
    startX = e.clientX
    startY = e.clientY
    startW = width.value
    startH = height.value
    document.body.style.userSelect = 'none'
    document.body.style.cursor =
      dir === 'e' ? 'ew-resize' : dir === 's' ? 'ns-resize' : 'nwse-resize'
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onEnd)
  }

  onBeforeUnmount(onEnd)

  return {
    width,
    height,
    resizing,
    resetSize,
    startResize,
    endResize: onEnd,
  }
}
