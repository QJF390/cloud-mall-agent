<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { chatApi, createChatSocket } from '@/api/chat'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const PAGE_SIZE = 20

const sessions = ref([])
const messages = ref([])
const activeSessionId = ref('')
const peerId = ref(null)
const inputText = ref('')
const wsState = ref('connecting')
const historyLoading = ref(false)
const hasMore = ref(true)
const listRef = ref(null)

/** 上一页最小消息ID（游标），首次为 null */
let cursor = null
let socket = null

const myId = computed(() => Number(userStore.userId))
/** 对方展示名：后端下发（客服=「官方客服」），取不到再退回 ID */
const peerName = computed(
  () =>
    activeSession.value?.peerName ||
    (peerId.value != null ? `用户 ${peerId.value}` : '')
)
const activeSession = computed(
  () => sessions.value.find((s) => s.sessionId === activeSessionId.value) || null
)
const canSend = computed(
  () => !!activeSessionId.value && wsState.value === 'open' && inputText.value.trim().length > 0
)

/** 客户端生成 msgId：服务端用它做幂等，前端用它把「回执」和「本地消息」对上 */
function genMsgId() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function toLocalMessage(m) {
  return {
    msgId: m.msgId,
    sessionId: m.sessionId,
    senderId: Number(m.senderId),
    receiverId: Number(m.receiverId),
    content: m.content,
    createTime: Number(m.createTime),
    serverMsgId: m.serverMsgId ?? null,
    status: 'sent'
  }
}

// ==================== 会话 ====================

async function loadSessions() {
  try {
    const res = await chatApi.sessions()
    sessions.value = res.data || []
  } catch (e) {
    console.warn('会话列表加载失败', e)
  }
}

async function selectSession(session) {
  if (!session) return
  activeSessionId.value = session.sessionId
  peerId.value = Number(session.peerId)
  session.unread = 0
  await loadHistory(true)
  // 已读上报：清掉服务端未读计数（失败不影响本页展示，下一进页面还会清）
  chatApi.markRead(session.sessionId).catch(() => {})
}

async function openSessionByPeer() {
  const { peer, productId, productName } = route.query
  if (!peer) return
  const res = await chatApi.createSession({
    sellerId: Number(peer),
    productId: productId ? Number(productId) : null,
    productName: productName || null
  })
  const created = res.data
  await loadSessions()
  const target = sessions.value.find((s) => s.sessionId === created.sessionId) || created
  await selectSession(target)
}

// ==================== 历史消息 ====================

async function loadHistory(reset) {
  if (!activeSessionId.value) return
  historyLoading.value = true
  try {
    const res = await chatApi.history(activeSessionId.value, reset ? null : cursor, PAGE_SIZE)
    const list = (res.data || []).map(toLocalMessage)
    if (reset) {
      // 服务端是倒序返回（id DESC），翻成正序展示
      messages.value = list.slice().reverse()
    } else {
      messages.value = list.slice().reverse().concat(messages.value)
    }
    cursor = list.length ? Math.min(...list.map((m) => Number(m.serverMsgId || 0))) : null
    hasMore.value = list.length === PAGE_SIZE
    if (reset) scrollToBottom()
  } catch (e) {
    console.warn('历史消息加载失败', e)
  } finally {
    historyLoading.value = false
  }
}

async function loadMore() {
  if (!hasMore.value || historyLoading.value) return
  const el = listRef.value
  const oldHeight = el?.scrollHeight || 0
  await loadHistory(false)
  nextTick(() => {
    // 保持滚动位置：向上加载更多时不能跳到顶部，否则体验割裂
    if (el) el.scrollTop = el.scrollHeight - oldHeight
  })
}

// ==================== 发送 / 接收 ====================

function sendMessage() {
  const text = inputText.value.trim()
  if (!text || !activeSessionId.value || peerId.value == null) return

  const msgId = genMsgId()
  const payload = {
    type: 'chat',
    msgId,
    sessionId: activeSessionId.value,
    receiverId: peerId.value,
    msgType: 1,
    content: text
  }
  // 乐观渲染：先上屏再等回执，用户感知的延迟 = 0。
  // 对应的代价是要有「失败态」，所以 send 返回 false 时标记为 failed 并支持重试。
  const ok = socket?.send(payload)
  messages.value.push({
    ...payload,
    senderId: myId.value,
    createTime: Date.now(),
    status: ok ? 'sending' : 'failed'
  })
  inputText.value = ''
  scrollToBottom()
  if (!ok) console.warn('连接不可用，消息未发出')
}

function retry(msg) {
  const ok = socket?.send({
    type: 'chat',
    msgId: msg.msgId,
    sessionId: msg.sessionId,
    receiverId: msg.receiverId,
    msgType: 1,
    content: msg.content
  })
  msg.status = ok ? 'sending' : 'failed'
}

function onFrame(frame) {
  // 回执：服务端已落库。本地有这条 → 改成已送达；本地没有 → 说明是自己在别的端发的，直接上屏
  if (frame.type === 'ack') {
    const hit = messages.value.find((m) => m.msgId === frame.msgId)
    if (hit) {
      hit.status = 'sent'
      hit.serverMsgId = frame.serverMsgId
      hit.createTime = frame.createTime || hit.createTime
    } else if (frame.sessionId === activeSessionId.value) {
      messages.value.push(toLocalMessage(frame))
      scrollToBottom()
    }
    return
  }

  if (frame.type === 'chat') {
    if (frame.sessionId === activeSessionId.value) {
      if (!messages.value.some((m) => m.msgId === frame.msgId)) {
        messages.value.push(toLocalMessage(frame))
        scrollToBottom()
      }
      chatApi.markRead(frame.sessionId).catch(() => {})
    } else {
      // 别的会话来的消息：只更新会话列表（未读 + 摘要），不打断当前聊天
      loadSessions()
    }
    return
  }

  if (frame.type === 'error') {
    const hit = messages.value.find((m) => m.msgId === frame.msgId)
    if (hit) hit.status = 'failed'
    console.warn('服务端返回错误:', frame.message)
  }
}

/** 滚到顶部自动加载更早的消息 */
function onListScroll(e) {
  if (e.target.scrollTop < 24) loadMore()
}

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(Number(ts))
  const pad = (n) => String(n).padStart(2, '0')
  const today = new Date()
  const sameDay = d.toDateString() === today.toDateString()
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return sameDay ? hm : `${d.getMonth() + 1}月${d.getDate()}日 ${hm}`
}

// ==================== 生命周期 ====================

onMounted(async () => {
  if (!userStore.isLogin) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  socket = createChatSocket({ onMessage: onFrame, onStatus: (s) => (wsState.value = s) })

  await loadSessions()
  if (route.query.session) {
    const target = sessions.value.find((s) => s.sessionId === route.query.session)
    if (target) await selectSession(target)
  } else if (route.query.peer) {
    await openSessionByPeer()
  } else if (sessions.value.length) {
    await selectSession(sessions.value[0])
  }
})

onBeforeUnmount(() => socket?.close())

// 监听路由变化：从商品详情点「联系客服」进来时复用同一个页面
watch(
  () => route.query.peer,
  async (peer) => {
    if (peer) await openSessionByPeer()
  }
)
</script>

<template>
  <div class="chat container">
    <aside class="session-pane">
      <header class="pane-head">
        <h1 class="pane-title">消息</h1>
        <span class="ws-dot" :class="`ws-${wsState}`" :title="wsState">
          {{ wsState === 'open' ? '已连接' : wsState === 'connecting' ? '连接中' : '重连中' }}
        </span>
      </header>

      <div v-if="!sessions.length" class="empty-sessions">还没有会话，去商品页联系客服聊聊吧</div>

      <ul v-else class="session-list">
        <li
          v-for="s in sessions"
          :key="s.sessionId"
          :class="['session-item', { active: s.sessionId === activeSessionId }]"
          @click="selectSession(s)"
        >
          <div class="avatar" :class="{ online: s.peerOnline }">
            {{ String(s.peerId).slice(-2) }}
          </div>
          <div class="session-main">
            <div class="session-row">
              <span class="peer">{{ s.peerName || `用户 ${s.peerId}` }}</span>
              <span class="time">{{ formatTime(s.lastMessageTime ? Date.parse(s.lastMessageTime) : 0) }}</span>
            </div>
            <div class="session-row">
              <span class="digest">
                <em v-if="s.productName" class="from">[{{ s.productName }}]</em>
                {{ s.lastMessage || '暂无消息' }}
              </span>
              <span v-if="s.unread" class="badge">{{ s.unread > 99 ? '99+' : s.unread }}</span>
            </div>
          </div>
        </li>
      </ul>
    </aside>

    <section class="chat-pane">
      <template v-if="activeSessionId">
        <header class="chat-head">
          <h2 class="chat-title">
            {{ peerName || `用户 ${peerId}` }}
            <span class="peer-state">{{ activeSession?.peerOnline ? '在线' : '离线' }}</span>
          </h2>
          <span class="chat-sub">{{ activeSession?.productName ? `咨询：${activeSession.productName}` : '私信会话' }}</span>
        </header>

        <div ref="listRef" class="msg-list" @scroll="onListScroll">
          <button v-if="hasMore" class="more" :disabled="historyLoading" @click="loadMore">
            {{ historyLoading ? '加载中…' : '查看更早的消息' }}
          </button>

          <div
            v-for="m in messages"
            :key="m.msgId"
            :class="['msg-row', { mine: m.senderId === myId }]"
          >
            <div class="bubble">
              <p class="text">{{ m.content }}</p>
              <div class="meta">
                <span>{{ formatTime(m.createTime) }}</span>
                <span v-if="m.status === 'sending'" class="st">发送中…</span>
                <span v-else-if="m.status === 'failed'" class="st failed" @click="retry(m)">
                  发送失败 · 重试
                </span>
              </div>
            </div>
          </div>
        </div>

        <footer class="composer">
          <textarea
            v-model="inputText"
            class="input"
            rows="2"
            maxlength="2000"
            placeholder="输入消息，Enter 发送 / Shift+Enter 换行"
            @keydown.enter.exact.prevent="sendMessage"
          ></textarea>
          <button class="send" :disabled="!canSend" @click="sendMessage">发送</button>
        </footer>
      </template>

      <div v-else class="blank">
        <p>选择一个会话开始聊天</p>
        <router-link class="to-market" to="/products">去市集逛逛 →</router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat {
  display: flex;
  gap: 16px;
  padding-top: 40px;
  padding-bottom: 40px;
  height: calc(100vh - var(--nav-h) - 80px);
  min-height: 520px;
}

/* ---------- 会话列表 ---------- */
.session-pane {
  flex: 0 0 300px;
  display: flex;
  flex-direction: column;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--r-lg);
  overflow: hidden;
}
.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid var(--c-line);
}
.pane-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 18px;
  letter-spacing: 0.08em;
}
.ws-dot {
  font-size: 12px;
  color: var(--c-text-mute);
}
.ws-open {
  color: var(--c-success);
}
.ws-connecting,
.ws-reconnecting {
  color: var(--c-warn);
}

.empty-sessions {
  padding: 32px 18px;
  font-size: 13px;
  color: var(--c-text-mute);
  text-align: center;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  margin: 0;
  padding: 6px;
  list-style: none;
}
.session-item {
  display: flex;
  gap: 10px;
  padding: 10px;
  border-radius: var(--r-md);
  cursor: pointer;
  transition: background 0.25s var(--ease);
}
.session-item:hover {
  background: var(--c-bg-deep);
}
.session-item.active {
  background: var(--c-accent-soft);
}
.avatar {
  position: relative;
  flex: 0 0 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--c-bg-deep);
  color: var(--c-text-sub);
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar.online::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 0;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--c-success);
  border: 2px solid var(--c-surface);
}
.session-main {
  flex: 1;
  min-width: 0;
}
.session-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.peer {
  font-size: 14px;
  color: var(--c-text);
}
.time {
  font-size: 11px;
  color: var(--c-text-mute);
}
.digest {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: var(--c-text-sub);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.from {
  color: var(--c-accent-deep);
  font-style: normal;
  margin-right: 3px;
}
.badge {
  flex: 0 0 auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--r-pill);
  background: var(--c-accent);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

/* ---------- 聊天区 ---------- */
.chat-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--r-lg);
  overflow: hidden;
}
.chat-head {
  padding: 14px 20px;
  border-bottom: 1px solid var(--c-line);
}
.chat-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
}
.peer-state {
  margin-left: 8px;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-mute);
}
.chat-sub {
  font-size: 12px;
  color: var(--c-text-mute);
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  background: var(--c-bg);
}
.more {
  display: block;
  margin: 0 auto 12px;
  padding: 4px 12px;
  border-radius: var(--r-pill);
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  color: var(--c-text-sub);
  font-size: 12px;
  cursor: pointer;
}
.msg-row {
  display: flex;
  margin-bottom: 12px;
}
.msg-row.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: 68%;
  padding: 9px 13px;
  border-radius: 14px;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  box-shadow: var(--sh-card);
}
.msg-row.mine .bubble {
  background: var(--c-accent-soft);
  border-color: #ffd9c2;
}
.text {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--c-text);
  word-break: break-word;
  white-space: pre-wrap;
}
.meta {
  margin-top: 5px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  font-size: 11px;
  color: var(--c-text-mute);
}
.st.failed {
  color: var(--c-danger);
  cursor: pointer;
}

.composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--c-line);
  background: var(--c-surface);
}
.input {
  flex: 1;
  resize: none;
  padding: 9px 12px;
  border: 1px solid var(--c-line);
  border-radius: var(--r-md);
  background: var(--c-bg);
  font: inherit;
  font-size: 14px;
  color: var(--c-text);
  outline: none;
  transition: border-color 0.25s var(--ease);
}
.input:focus {
  border-color: var(--c-accent);
}
.send {
  height: 38px;
  padding: 0 22px;
  border-radius: var(--r-pill);
  background: var(--c-accent);
  color: #fff;
  font-size: 14px;
  box-shadow: var(--sh-accent);
  transition: opacity 0.25s var(--ease), transform 0.25s var(--ease);
}
.send:disabled {
  opacity: 0.45;
  box-shadow: none;
  cursor: not-allowed;
}
.send:not(:disabled):hover {
  transform: translateY(-1px);
}

.blank {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--c-text-mute);
  font-size: 14px;
}
.to-market {
  color: var(--c-accent-deep);
}

@media (max-width: 768px) {
  .chat {
    flex-direction: column;
    padding-top: 24px;
    height: calc(100vh - var(--nav-h) - 40px);
  }
  /* 窄屏改成「上列表 / 下聊天」，避免两栏都挤成一条 */
  .session-pane {
    flex: 0 0 32%;
  }
  .bubble {
    max-width: 80%;
  }
}
</style>
