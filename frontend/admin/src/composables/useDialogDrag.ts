import { onBeforeUnmount, ref } from 'vue'

/** AppModal 标题栏拖拽：相对打开时位置偏移 */
export function useDialogDrag() {
  const offsetX = ref(0)
  const offsetY = ref(0)
  const dragging = ref(false)

  let startClientX = 0
  let startClientY = 0
  let originX = 0
  let originY = 0

  function resetPosition() {
    offsetX.value = 0
    offsetY.value = 0
  }

  function onMove(e: MouseEvent) {
    if (!dragging.value) return
    offsetX.value = originX + (e.clientX - startClientX)
    offsetY.value = originY + (e.clientY - startClientY)
  }

  function onUp() {
    if (!dragging.value) return
    dragging.value = false
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }

  function startDrag(e: MouseEvent) {
    if (e.button !== 0) return
    const target = e.target as HTMLElement | null
    if (target?.closest('button, a, input, textarea, select, .el-icon')) return
    e.preventDefault()
    dragging.value = true
    startClientX = e.clientX
    startClientY = e.clientY
    originX = offsetX.value
    originY = offsetY.value
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }

  onBeforeUnmount(() => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  })

  return {
    offsetX,
    offsetY,
    dragging,
    resetPosition,
    startDrag,
  }
}
