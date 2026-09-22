import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 开发环境通过 Vite 代理把 /api 转发到网关（localhost:8080），
 * 这样浏览器视角是同源请求，规避跨域预检；
 * 鉴权仍然在网关完成（代理只做转发，不改变任何鉴权逻辑）。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // ws: true —— 必须显式打开，否则 WebSocket 的 Upgrade 请求会被当成普通 HTTP 转发，握手必然 400
        ws: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 后台新上传的商品图片存在 /uploads/**（由 admin-service 托管、网关转发）。
      // 不代理的话，C 端 <img src="/uploads/xxx.png"> 会打到 Vite 自己的静态目录 → 404 裂图。
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // AI 服务（Python / FastAPI，端口 8000）。
      // 开发阶段直连 ai-service：避免每调一次接口都要改 Nacos 路由 + 重启网关。
      // 生产环境应由网关的 /ai/** 路由承接，届时删掉这段代理即可。
      '/ai': {
        target: 'http://localhost:8000',
        changeOrigin: true
      }
    }
  }
})
