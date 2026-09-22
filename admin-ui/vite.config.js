import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 后台管理前端开发配置。
 * - 端口 5174，避免与 C 端 web-ui（5173）冲突
 * - /api 代理到网关 8080，鉴权与路由仍由网关统一处理
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5174,
    open: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 上传的商品图片：图片地址是 /uploads/**（后端返回的相对 URL），
      // 走网关统一入口，避免前端出现第二个后端地址。
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
