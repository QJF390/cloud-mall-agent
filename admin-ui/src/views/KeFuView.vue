<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { kefuApi } from '@/api/chat'

const SESSION_POLL_MS = 5000

const sessions = ref([])
const messages = ref([])
const activeSessionId = ref('')
const peerId = ref(null)
const inputText = ref('')
const loading = ref(false)
const sending = ref(false)
const errMsg = ref('')
const listRef = ref(null)

let pollTimer = null

const activeSession = computed(
  () => sessions.value.find((s) => s.sessionId === activeSessionId.value) || null
)
const canSend = computed(() => !!activeSessionId.value && inputText.value.trim().length > 0 && !sending.value)

function pad(n) {
  return String(n ?? 0).padStart(2, '0')
}

/** 后端 LocalDateTime 可能是数组 / ISO 字符串 / 时间戳，统一容错 */
function formatTime(t) {
  if (!t) return ''
  if (Array.isArray(t)) {
    const [y, m, d, hh, mm] = t
    return `${pad(m)}-${pad(d)} ${pad(hh)}:${pad(mm)}`
  }
  const date = new Date(typeof t === 'string' ? t.replace(' ', 'T') : t)
  if (Number.isNaN(date.getTime())) return ''
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function loadSessions(keepActive = true) {
  try {
    const res = await kefuApi.sessions()
    sessions.value = res.data || []
    if (keepActive && activeSessionId.value) {
      // 会话可能已被关闭/删除，保持选中态前先确认它还在
      if (!sessions.value.some((s) => s.sessionId === activeSessionId.value)) {
        activeSessionId.value = ''
        messages.value = []
      }
    }
    if (!activeSessionId.value && sessions.value.length) {
      await selectSession(sessions.value[0])
    }
  } catch (e) {
    errMsg.value = e.message || '会话列表加载失败'
  }
}

async function selectSession(session) {
  if (!session) return
  // 先清空再加载：merge 是按 msgId 归并的，不清空的话
  // 上一个会话的消息会原样留下来，和当前会话混在一个窗口里
  messages.value = []
  activeSessionId.value = session.sessionId
  peerId.value = Number(session.peerId)
  session.unread = 0
  await loadMessages()
  kefuApi.read(session.sessionId).catch(() => {})
  scrollToBottom()
}

async function loadMessages() {
  const sessionId = activeSessionId.value
  if (!sessionId) return
  try {
    const res = await kefuApi.history(sessionId, null, 50)
    // 竞态防线：轮询在途时用户可能已切到别的会话，
    // 旧会话的响应晚到会污染新会话的窗口，必须丢弃
    if (sessionId !== activeSessionId.value) return
    // 服务端倒序返回，翻成正序展示；按 msgId 归并，避免轮询造成重复/闪烁
    const list = (res.data || []).slice().reverse()
    const merged = new Map(messages.value.map((m) => [m.msgId, m]))
    list.forEach((m) => merged.set(m.msgId, m))
    // 按 DB 自增 id 排序：msgId 是 UUID 不可比，之前误用了不存在的 serverMsgId，
    // 导致所有 key 都是 0、顺序退化成插入序 —— 切会话后的旧消息反而排在前面
    messages.value = [...merged.values()].sort((a, b) => (a.id || 0) - (b.id || 0))
  } catch (e) {
    if (sessionId === activeSessionId.value) {
      errMsg.value = e.message || '消息加载失败'
    }
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || !canSend.value) return
  sending.value = true
  try {
    await kefuApi.reply(activeSessionId.value, text)
    inputText.value = ''
    await loadMessages()
    await loadSessions()
    scrollToBottom()
  } catch (e) {
    errMsg.value = e.message || '发送失败，请重试'
  } finally {
    sending.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** 轮询：会话列表 + 当前会话消息。页面不可见时不打接口，省资源 */
async function tick() {
  if (document.hidden) return
  await Promise.all([loadSessions(), loadMessages()])
}

onMounted(async () => {
  loading.value = true
  await loadSessions()
  loading.value = false
  pollTimer = setInterval(tick, SESSION_POLL_MS)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <section class="kefu">
    <aside class="session-pane">
      <header class="pane-head">
        <h2>咨询会话</h2>
        <span class="count">{{ sessions.length }}</span>
      </header>

      <div v-if="errMsg" class="tip-err">{{ errMsg }}</div>

      <div v-if="!sessions.length && !loading" class="empty">暂无用户咨询</div>

      <ul class="session-list">
        <li
          v-for="s in sessions"
          :key="s.sessionId"
          :class="['session-item', { active: s.sessionId === activeSessionId }]"
          @click="selectSession(s)"
        >
          <div class="avatar">{{ String(s.peerId).slice(-2) }}</div>
          <div class="session-main">
            <div class="row">
              <span class="name">{{ s.peerName || `用户 ${s.peerId}` }}</span>
              <span class="time">{{ formatTime(s.lastMessageTime) }}</span>
            </div>
            <div class="row">
              <span class="digest">{{ s.lastMessage || '（暂无消息）' }}</span>
              <span v-if="s.unread" class="badge">{{ s.unread }}</span>
            </div>
            <div v-if="s.productName" class="product">咨询：{{ s.productName }}</div>
          </div>
        </li>
      </ul>
    </aside>

    <section class="chat-pane">
      <template v-if="activeSessionId">
        <header class="chat-head">
          <h3>{{ activeSession?.peerName || `用户 ${peerId}` }}</h3>
          <span v-if="activeSession?.productName" class="sub">咨询：{{ activeSession?.productName }}</span>
        </header>

        <div ref="listRef" class="msg-list">
          <div
            v-for="m in messages"
            :key="m.msgId"
            :class="['msg-row', Number(m.senderId) === Number(peerId) ? 'theirs' : 'mine']"
          >
            <div class="bubble">
              <div class="text">{{ m.content }}</div>
              <div class="meta">{{ formatTime(m.createTime) }}</div>
            </div>
          </div>
        </div>

        <footer class="composer">
          <textarea
            v-model="inputText"
            rows="2"
            placeholder="以「官方客服」身份回复（Ctrl + Enter 发送）"
            @keydown.ctrl.enter.prevent="sendMessage"
          ></textarea>
          <button class="btn-primary" :disabled="!canSend" @click="sendMessage">
            {{ sending ? '发送中…' : '发送' }}
          </button>
        </footer>
      </template>

      <div v-else class="empty-chat">从左侧选择一个会话开始接待</div>
    </section>
  </section>
</template>

<style scoped>
.kefu {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  height: calc(100vh - 140px);
  min-height: 460px;
}

.session-pane,
.chat-pane {
  display: flex;
  flex-direction: column;
  background: var(--c-card);
  border: 1px solid var(--c-border);
  border-radius: 10px;
  overflow: hidden;
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--c-border);
}
.pane-head h2 {
  margin: 0;
  font-size: 15px;
}
.count {
  font-size: 12px;
  color: var(--c-text-sub);
}

.session-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  flex: 1;
}
.session-item {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  border-bottom: 1px solid var(--c-border);
}
.session-item:hover {
  background: #f7f9fd;
}
.session-item.active {
  background: #eef3ff;
}
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--c-primary);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.session-main {
  flex: 1;
  min-width: 0;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.name {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}
.time {
  font-size: 11px;
  color: var(--c-text-sub);
}
.digest {
  font-size: 12px;
  color: var(--c-text-sub);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 170px;
}
.badge {
  background: var(--c-danger);
  color: #fff;
  font-size: 11px;
  border-radius: 9px;
  padding: 0 6px;
  line-height: 16px;
}
.product {
  margin-top: 4px;
  font-size: 11px;
  color: var(--c-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-head {
  padding: 14px 16px;
  border-bottom: 1px solid var(--c-border);
}
.chat-head h3 {
  margin: 0;
  font-size: 15px;
}
.sub {
  font-size: 12px;
  color: var(--c-text-sub);
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafbfe;
}
.msg-row {
  display: flex;
  margin-bottom: 12px;
}
.msg-row.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: 62%;
  padding: 9px 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid var(--c-border);
}
.msg-row.mine .bubble {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
}
.text {
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.meta {
  margin-top: 4px;
  font-size: 11px;
  color: var(--c-text-sub);
}
.msg-row.mine .meta {
  color: rgba(255, 255, 255, 0.8);
}

.composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px;
  border-top: 1px solid var(--c-border);
}
.composer textarea {
  flex: 1;
  resize: none;
  padding: 8px 10px;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  font-size: 13px;
  font-family: inherit;
}
.composer textarea:focus {
  outline: none;
  border-color: var(--c-primary);
}

.empty,
.empty-chat,
.tip-err {
  padding: 16px;
  font-size: 13px;
  color: var(--c-text-sub);
}
.tip-err {
  color: var(--c-danger);
}
.empty-chat {
  margin: auto;
}
</style>
