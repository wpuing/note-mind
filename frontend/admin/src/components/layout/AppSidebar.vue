<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Odometer,
  User,
  Collection,
  Document,
  Files,
  SetUp,
  Search,
  DataBoard,
  Cpu,
  EditPen,
  Tools,
  List,
  Timer,
  ChatDotRound,
  Notebook,
  DataAnalysis,
  Histogram,
  TrendCharts,
  Avatar,
  Setting,
} from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const collapsed = computed(() => appStore.sidebarCollapsed)
const active = computed(() => route.path)

type MenuItem = {
  index: string
  title: string
  icon?: unknown
  children?: MenuItem[]
}

const menus: MenuItem[] = [
  { index: '/dashboard', title: '工作台', icon: Odometer },
  // 顶层可测入口（不进子菜单，避免找不到）
  { index: '/knowledge/document', title: '① 文件管理', icon: Document },
  { index: '/knowledge/retrieval-test', title: '② 检索测试', icon: DataBoard },
  { index: '/knowledge/recall-bench', title: '③ 召回调试台', icon: TrendCharts },
  { index: '/app', title: '④ 问答测试', icon: ChatDotRound },
  { index: '/ai/model', title: '模型配置', icon: Cpu },
  { index: '/user', title: '用户管理', icon: User },
  {
    index: 'knowledge',
    title: '知识库',
    icon: Collection,
    children: [
      { index: '/knowledge/base', title: '知识库管理', icon: Collection },
      { index: '/knowledge/chunk-strategy', title: '切分策略', icon: SetUp },
      { index: '/knowledge/retrieval-strategy', title: '检索策略', icon: Search },
      { index: '/knowledge/document', title: '文件管理', icon: Document },
      { index: '/knowledge/segment', title: '片段管理', icon: Files },
      { index: '/knowledge/retrieval-test', title: '检索测试', icon: DataBoard },
      { index: '/knowledge/recall-bench', title: '召回调试台', icon: TrendCharts },
    ],
  },
  {
    index: 'ai',
    title: 'AI 配置',
    icon: Cpu,
    children: [
      { index: '/ai/model', title: '模型配置', icon: Cpu },
      { index: '/ai/prompt', title: 'Prompt 模板', icon: EditPen },
      { index: '/ai/tool', title: '工具中心', icon: Tools },
      { index: '/ai/tool-log', title: '调用日志', icon: List },
      { index: '/ai/agent', title: 'Agent 执行', icon: Cpu },
      { index: '/ai/agent-run', title: 'Agent 运行记录', icon: Timer },
    ],
  },
  {
    index: 'qa',
    title: '问答应用',
    icon: ChatDotRound,
    children: [
      { index: '/app/manage', title: '应用管理', icon: SetUp },
      { index: '/app', title: '问答测试', icon: ChatDotRound },
      { index: '/chat-log', title: '对话日志与反馈', icon: Notebook },
    ],
  },
  {
    index: 'eval',
    title: '效果评测',
    icon: DataAnalysis,
    children: [
      { index: '/eval/dataset', title: '评测集管理', icon: Collection },
      { index: '/eval/batch', title: '批量评测', icon: Histogram },
      { index: '/eval/compare', title: '策略对比', icon: TrendCharts },
    ],
  },
  { index: '/profile', title: '个人资料', icon: Avatar },
  { index: '/system/config', title: '系统配置', icon: Setting },
]

const openeds = computed(() => {
  const p = route.path
  if (p.startsWith('/knowledge')) return ['knowledge']
  if (p.startsWith('/ai')) return ['ai']
  if (p === '/app' || p.startsWith('/app/') || p.startsWith('/chat-log')) return ['qa']
  if (p.startsWith('/eval')) return ['eval']
  return []
})

function onSelect(index: string) {
  if (index.startsWith('/')) router.push(index)
}
</script>

<template>
  <aside class="sidebar" :class="{ collapsed }">
    <div class="brand" @click="router.push('/dashboard')">
      <span class="brand-mark" aria-hidden="true">
        <span class="leaf" />
      </span>
      <div v-show="!collapsed" class="brand-text">
        <strong>NoteMind</strong>
        <small>Admin</small>
      </div>
    </div>

    <el-scrollbar class="menu-scroll">
      <el-menu
        :default-active="active"
        :default-openeds="openeds"
        :collapse="collapsed"
        :collapse-transition="false"
        class="side-menu"
        @select="onSelect"
      >
        <template v-for="item in menus" :key="item.index">
          <el-sub-menu v-if="item.children?.length" :index="item.index">
            <template #title>
              <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.index" :index="child.index">
              <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.index">
            <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-scrollbar>

    <div v-show="!collapsed" class="sidebar-foot">
      <span class="dot" />
      AI 企业知识库 · Admin
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 10px 12px;
  border-right: 1px solid var(--line);
  background:
    linear-gradient(180deg, rgba(61, 93, 51, 0.96) 0%, rgba(44, 63, 38, 0.98) 100%);
  color: #eef6ea;
  overflow: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px 14px;
  cursor: pointer;
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.12);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
  flex-shrink: 0;
}

.leaf {
  width: 14px;
  height: 18px;
  border-radius: 0 12px 2px 12px;
  background: linear-gradient(160deg, #c7dec0, #7fad6c 55%, #4b743d);
  transform: rotate(-18deg);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  min-width: 0;
}

.brand-text strong {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.brand-text small {
  font-size: 11px;
  opacity: 0.7;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.menu-scroll {
  flex: 1;
  min-height: 0;
}

.side-menu {
  border-right: 0;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-text-color: rgba(238, 246, 234, 0.82);
  --el-menu-active-color: #fff;
  --el-menu-item-height: 44px;
}

.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  border-radius: 12px;
  margin: 2px 4px;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(127, 173, 108, 0.35), rgba(127, 173, 108, 0.08));
  box-shadow: inset 3px 0 0 #a3c794;
  font-weight: 600;
}

.side-menu :deep(.el-sub-menu .el-menu) {
  background: transparent;
}

.side-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  padding-left: 48px !important;
}

.sidebar.collapsed .brand {
  justify-content: center;
  padding-inline: 0;
}

.sidebar-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 8px 4px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  font-size: 12px;
  color: rgba(238, 246, 234, 0.72);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #a3c794;
  box-shadow: 0 0 0 4px rgba(163, 199, 148, 0.2);
}

@media (max-width: 900px) {
  .sidebar {
    position: fixed;
    z-index: 40;
    left: 0;
    top: 0;
    width: min(280px, 86vw);
    transform: translateX(0);
    box-shadow: 8px 0 24px rgba(0, 0, 0, 0.18);
  }

  .sidebar.collapsed {
    transform: translateX(-110%);
  }
}
</style>
