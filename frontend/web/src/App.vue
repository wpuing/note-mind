<script setup lang="ts">
/**
 * 根组件：仅渲染路由出口，具体页面由 vue-router 决定。
 */
import { RouterView } from 'vue-router'
</script>

<template>
  <!-- 路由匹配的页面组件在此渲染 -->
  <RouterView />
</template>
