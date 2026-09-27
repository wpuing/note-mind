import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5174,
    strictPort: true,
    proxy: {
      // 仅探测 AI 健康；业务 AI 一律经 Gateway→Service→AiEngineClient，勿再代理 /api/v1/ai/
      '/health': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        // 大文件解析 / OCR 经 Gateway 可能数分钟
        timeout: 600_000,
        proxyTimeout: 600_000,
      },
    },
  },
})
