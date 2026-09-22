import { getToken } from '@/utils/auth'

const AI_BASE = import.meta.env.VITE_AI_BASE || '/ai'

const SESSION_KEY = 'sgz_ai_session'

/** 会话 ID：同一浏览器保持同一会话，刷新页面不丢上下文 */
export function getOrCreateSessionId() {
  let sid = localStorage.getItem(SESSION_KEY)
  if (!sid) {
    sid = `sess_${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`
    localStorage.setItem(SESSION_KEY, sid)
  }
  return sid
}

/** 新会话：重置 ID，服务端旧历史自然由 TTL 过期回收 */
export function resetSessionId() {
  localStorage.removeItem(SESSION_KEY)
  return getOrCreateSessionId()
}

/**
 * 解析单个 SSE 事件块（按 SSE 规范）
 * 块格式：event: xxx\ndata: yyy\n 多条 data 行用换行拼接
 */
function parseSSEBlock(block) {
  if (!block || !block.trim()) return null

  let event = 'message'
  const dataLines = []

  for (const line of block.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue // 以冒号开头是注释/心跳，按规范忽略
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }

  if (!dataLines.length) return null
  return { event, data: dataLines.join('\n') }
}

export async function chatStream({ sessionId, message, signal, onChunk, onDone, onGated }) {
  const token = getToken()

  const resp = await fetch(`${AI_BASE}/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { satoken: token } : {})
    },
    body: JSON.stringify({ session_id: sessionId, message }),
    signal
  })

  if (!resp.ok) {
    const text = await resp.text().catch(() => '')
    throw new Error(`AI 服务返回 ${resp.status}${text ? `：${text.slice(0, 160)}` : ''}`)
  }
  if (!resp.body) throw new Error('当前浏览器不支持流式响应')

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    // SSE 以「空行」分隔事件；最后一段可能不完整，留在 buffer 里
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() ?? ''

    for (const block of blocks) {
      const parsed = parseSSEBlock(block)
      if (!parsed) continue

      try {
        const payload = JSON.parse(parsed.data)

        if (parsed.event === 'done') {
          if (payload.status && payload.status !== 'ok') {
            onGated?.(payload.reason || 'unknown')
          }
          onDone?.()
          continue
        }
        if (payload.status === 'ok') {
          onDone?.()
          continue
        }
        if (typeof payload.chunk === 'string' && payload.chunk) {
          onChunk?.(payload.chunk)
        }
      } catch {
        // 解析不了的内容（如服务端心跳）直接忽略，不影响主流程
      }
    }
  }
}

/**
 * 受理图片生成任务（异步，立即返回 taskId）
 * @param {string} description 用户对作品的描述
 * @param {'reference'|'banner'|'preview'} usageType 用途，禁止用于商品实物主图
 */
export async function requestImageTask(description, usageType = 'reference') {
  const token = getToken()

  const resp = await fetch(`${AI_BASE}/image/generate`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { satoken: token } : {})
    },
    body: JSON.stringify({ description, usage_type: usageType })
  })

  // 422 = 内容安全审核未通过，detail 是给用户看的人话
  if (!resp.ok) {
    let msg = `图片任务受理失败：${resp.status}`
    try {
      const body = await resp.json()
      if (body?.detail) msg = body.detail
    } catch {
      /* 非 JSON 响应，保留默认文案 */
    }
    throw new Error(msg)
  }
  return resp.json()
}

/**
 * 轮询生图任务结果
 * @param {string} taskId requestImageTask 返回的 task_id
 * @returns {{status:'pending'|'processing'|'success'|'failed', message, image_url, positive_prompt, error}}
 */
export async function getImageTaskResult(taskId) {
  const token = getToken()

  const resp = await fetch(`${AI_BASE}/image/result/${encodeURIComponent(taskId)}`, {
    headers: token ? { satoken: token } : {}
  })

  if (resp.status === 404) throw new Error('任务不存在或已过期，请重新生成')
  if (!resp.ok) throw new Error(`查询任务状态失败：${resp.status}`)
  return resp.json()
}
