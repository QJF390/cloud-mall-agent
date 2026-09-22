import request from './request'
import { getToken } from '@/utils/auth'

export const chatApi = {
  /** 平台客服信息（B2C 自营：咨询对象就是它，返回 { userId, nickname }） */
  kefu: () => request.get('/chat/kefu'),
  /** 发起 / 获取与某人的会话 */
  createSession: (data) => request.post('/chat/session/create', data),
  /** 我的会话列表 */
  sessions: () => request.get('/chat/session/list'),
  /** 历史消息：cursor 为空表示取最新一页 */
  history: (sessionId, cursor, size = 20) =>
    request.get('/chat/message/list', { params: { sessionId, cursor, size } }),
  /** 已读上报：清空我在该会话的未读 */
  markRead: (sessionId) => request.post('/chat/message/read', null, { params: { sessionId } }),
  /** 未读总数（导航角标） */
  unreadTotal: () => request.get('/chat/unread/total')
}

/** WS 基地址：开发环境由 Vite 把 /api 代理到网关 8080，并支持 WebSocket 升级 */
function resolveWsBase() {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return import.meta.env.VITE_WS_BASE || `${proto}//${location.host}/api`
}

export function createChatSocket({ onMessage, onStatus }) {
  let ws = null
  let heartbeatTimer = null
  let reconnectTimer = null
  let retryCount = 0
  let closedByUser = false

  function status(s) {
    onStatus?.(s)
  }

  function connect() {
    const token = getToken()
    if (!token) {
      status('no-token')
      return
    }
    status('connecting')
    ws = new WebSocket(`${resolveWsBase()}/chat/ws?satoken=${encodeURIComponent(token)}`)

    ws.onopen = () => {
      retryCount = 0
      status('open')
      // 心跳间隔要小于服务端空闲超时（服务端是 30s*3），这里取 25s
      heartbeatTimer = setInterval(() => {
        if (ws?.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' }))
        }
      }, 25000)
    }

    ws.onmessage = (event) => {
      let frame = null
      try {
        frame = JSON.parse(event.data)
      } catch {
        return
      }
      if (frame.type === 'pong') return
      onMessage?.(frame)
    }

    ws.onerror = () => status('error')

    ws.onclose = () => {
      clearInterval(heartbeatTimer)
      if (closedByUser) {
        status('closed')
        return
      }
      status('reconnecting')
      // 指数退避：1s → 2s → 4s … 上限 30s。
      // 不做退避的话，后端一重启，几万客户端同一秒重连 = 第二次雪崩。
      const delay = Math.min(30000, 1000 * 2 ** retryCount++)
      reconnectTimer = setTimeout(connect, delay)
    }
  }

  connect()

  return {
    /** 发送消息；返回 false 表示连接不可用，调用方应把消息标记为「发送失败」 */
    send(payload) {
      if (!ws || ws.readyState !== WebSocket.OPEN) return false
      ws.send(JSON.stringify(payload))
      return true
    },
    close() {
      closedByUser = true
      clearInterval(heartbeatTimer)
      clearTimeout(reconnectTimer)
      ws?.close()
    }
  }
}
