<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { useDialogResize, type ResizeDir } from '@/composables/useDialogResize'
import { useDialogDrag } from '@/composables/useDialogDrag'

/**
 * 自研遮罩弹窗（不依赖 el-dialog）。
 * EP Dialog 默认不挂 body，易被 layout/panel 裁切成「页面底部只露一块」。
 * 默认支持标题栏拖拽移动。
 */
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    variant?: 'form' | 'detail'
    width?: string | number
    top?: string
    destroyOnClose?: boolean
    closeOnClickModal?: boolean
    alignCenter?: boolean
    /** 是否允许拖拽标题栏移动，默认 true */
    draggable?: boolean
    resizable?: boolean
    defaultWidth?: number
    defaultHeight?: number
    minWidth?: number
    minHeight?: number
  }>(),
  {
    variant: 'form',
    destroyOnClose: true,
    closeOnClickModal: true,
    draggable: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [open: boolean]
  closed: []
  opened: []
}>()

const isDetail = computed(() => props.variant === 'detail')
const enableResize = computed(() => props.resizable ?? isDetail.value)
const useAlignCenter = computed(() =>
  props.alignCenter !== undefined ? props.alignCenter : !isDetail.value,
)

const {
  width: resizeW,
  height: resizeH,
  resizing,
  resetSize,
  startResize,
  endResize,
} = useDialogResize({
  defaultWidth: props.defaultWidth,
  defaultHeight: props.defaultHeight,
  minWidth: props.minWidth,
  minHeight: props.minHeight,
})

const { offsetX, offsetY, dragging, resetPosition, startDrag } = useDialogDrag()

const panelWidth = computed(() => {
  if (enableResize.value) return `${resizeW.value}px`
  const w = props.width ?? '760px'
  return typeof w === 'number' ? `${w}px` : w
})

const panelStyle = computed(() => {
  const style: Record<string, string> = { width: panelWidth.value }
  if (enableResize.value) {
    style.height = `${resizeH.value}px`
    style['--app-modal-h'] = `${resizeH.value}px`
  }
  if (!useAlignCenter.value) {
    style.marginTop = props.top ?? (isDetail.value ? '2vh' : '6vh')
  }
  if (props.draggable && (offsetX.value !== 0 || offsetY.value !== 0 || dragging.value)) {
    style.transform = `translate(${offsetX.value}px, ${offsetY.value}px)`
  }
  return style
})

const panelClass = computed(() => [
  'app-modal-panel-shell',
  isDetail.value ? 'app-modal-panel-shell--detail' : 'app-modal-panel-shell--form',
  {
    'is-resizing': resizing.value,
    'is-center': useAlignCenter.value,
    'is-dragging': dragging.value,
  },
])

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.modelValue) close()
}

watch(
  () => props.modelValue,
  (open, wasOpen) => {
    if (open) {
      if (enableResize.value) resetSize()
      resetPosition()
      document.body.style.overflow = 'hidden'
      window.addEventListener('keydown', onKeydown)
      emit('opened')
    } else {
      document.body.style.overflow = ''
      window.removeEventListener('keydown', onKeydown)
      endResize()
      if (wasOpen) emit('closed')
    }
  },
)

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
  endResize()
})

function close() {
  emit('update:modelValue', false)
}

function onMaskClick() {
  if (props.closeOnClickModal) close()
}

function onStartResize(dir: ResizeDir, e: MouseEvent) {
  startResize(dir, e)
}

function onHeadMouseDown(e: MouseEvent) {
  if (!props.draggable) return
  startDrag(e)
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="modelValue || !destroyOnClose"
      v-show="modelValue"
      class="app-modal-mask"
      :class="{ 'app-modal-mask--detail': isDetail, 'app-modal-mask--form': !isDetail }"
      role="dialog"
      aria-modal="true"
      @mousedown.self="onMaskClick"
    >
      <div :class="panelClass" :style="panelStyle" @mousedown.stop>
        <header
          class="app-modal-panel-shell__head"
          :class="{ 'is-draggable': draggable }"
          @mousedown="onHeadMouseDown"
        >
          <h3 class="app-modal-panel-shell__title">{{ title }}</h3>
          <button
            type="button"
            class="app-modal-panel-shell__close"
            aria-label="关闭"
            @click="close"
          >
            <el-icon :size="16"><Close /></el-icon>
          </button>
        </header>

        <div class="app-modal-panel-shell__body">
          <slot />
        </div>

        <footer v-if="$slots.footer" class="app-modal-panel-shell__foot">
          <slot name="footer" />
        </footer>

        <template v-if="enableResize">
          <div
            class="app-modal-resize app-modal-resize--e"
            title="拖拽调整宽度"
            @mousedown="onStartResize('e', $event)"
          />
          <div
            class="app-modal-resize app-modal-resize--se"
            title="拖拽调整大小"
            @mousedown="onStartResize('se', $event)"
          />
        </template>
      </div>
    </div>
  </Teleport>
</template>
