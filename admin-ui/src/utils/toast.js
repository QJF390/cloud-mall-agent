import { reactive } from 'vue'

/**
 * 轻量全局提示。
 * 不引第三方 UI 库，用一份响应式队列 + 固定定位渲染，够用且零依赖。
 */
let seq = 0

export const toasts = reactive([])

export function removeToast(id) {
  const index = toasts.findIndex((item) => item.id === id)
  if (index > -1) toasts.splice(index, 1)
}

function push(type, message, duration = 2600) {
  if (!message) return
  const id = ++seq
  toasts.push({ id, type, message })
  window.setTimeout(() => removeToast(id), duration)
}

export const toast = {
  success: (message) => push('success', message),
  error: (message) => push('error', message, 3600),
  info: (message) => push('info', message)
}

export default toast
