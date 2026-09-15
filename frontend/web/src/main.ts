/**
 * 用户端 Web 入口：挂载 Vue 应用、路由、Pinia 与 Element Plus（中文语言包）。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import '@/assets/styles/base.css'

// 创建根应用实例
const app = createApp(App)
// 注册全局状态
app.use(createPinia())
// 注册路由
app.use(router)
// 注册 UI 组件库并指定中文 locale
app.use(ElementPlus, { locale: zhCn })
// 挂载到 index.html 中的 #app
app.mount('#app')
