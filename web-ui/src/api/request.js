import axios from 'axios'
import { getToken, clearAuth } from '@/utils/auth'

/**
 * 统一请求封装
 * - baseURL 走 /api，由 Vite 代理转发到网关 8080（生产由 Nginx 承接）
 * - 请求头注入 satoken（Sa-Token 默认 token 名称）
 * - 统一解析后端 R 结构 { code, message, data, timestamp }
 * - 401 统一处理：清理登录态并回到登录页
 */
const baseURL = import.meta.env.VITE_API_BASE || '/api'

const request = axios.create({
  baseURL,
  timeout: 15000
})

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.satoken = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

function makeError(message, code) {
  const err = new Error(message)
  err.code = code
  return err
}

/** 未登录 / 登录过期：清理本地登录态并跳转登录页（带回跳地址） */
function redirectToLogin() {
  clearAuth()
  const current = window.location.pathname + window.location.search
  if (!current.startsWith('/login')) {
    window.location.href = `/login?redirect=${encodeURIComponent(current)}`
  }
}

request.interceptors.response.use(
  (response) => {
    const body = response.data

    // 后端统一 R 结构；网关 Sa-Token 拦截返回的是 { code: 401, msg } 结构
    if (body && typeof body.code === 'number') {
      if (body.code === 200) return body

      if (body.code === 401) {
        redirectToLogin()
        return Promise.reject(makeError(body.message || body.msg || '请先登录', 401))
      }
      return Promise.reject(makeError(body.message || body.msg || '请求失败', body.code))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data

    if (status === 401 || body?.code === 401) {
      redirectToLogin()
      return Promise.reject(makeError('登录已失效，请重新登录', 401))
    }

    let message = body?.message || body?.msg
    if (!message) {
      if (status === 503) message = '服务暂时不可用，请稍后重试'
      else if (status === 404) message = '接口不存在（检查网关路由）'
      else if (error.code === 'ECONNABORTED') message = '请求超时，请稍后重试'
      // 只有拿不到 response（status 为空）才是真正的"连不上"。
      // 有 status 说明后端是回了响应的，只是没带 message —— 那是服务端异常，
      // 一律报"网络异常"会把排查方向彻底带偏（后端其实已经 500 了）。
      else if (!status) message = '网络异常，请检查后端服务是否启动'
      else message = `服务端异常（HTTP ${status}），请查看后端日志`
    }
    return Promise.reject(makeError(message, status || 0))
  }
)

export default request
