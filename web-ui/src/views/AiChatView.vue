<script setup>
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'
import {
  chatStream,
  getOrCreateSessionId,
  resetSessionId,
  requestImageTask,
  getImageTaskResult
} from '@/api/ai'

const activeTab = ref('chat') // 'chat' | 'image'
const sessionId = ref(getOrCreateSessionId())
/** @type {import('vue').Ref<Array<{role:'user'|'assistant', content:string}>>} */
const messages = ref([])
const input = ref('')
const streaming = ref(false)
const errorMsg = ref('')

const listRef = ref(null)
let controller = null

/* ---------------- 灵感生图 ---------------- */
const imageDesc = ref('')
const usageType = ref('reference')
const generating = ref(false)
/** @type {import('vue').Ref<null|{status:string, message:string, image_url:string|null, positive_prompt:string|null, error:string|null}>} */
const imageTask = ref(null)
const imageError = ref('')
let pollTimer = null

const USAGE_OPTIONS = [
  { value: 'reference', label: '创作灵感图' },
  { value: 'banner', label: '装饰 Banner' },
  { value: 'preview', label: '定制预览图' }
]

const canGenerate = computed(
  () => imageDesc.value.trim().length > 0 && !generating.value
)

const SUGGESTIONS = [
  '平台发货时效是多久？',
  '帮我找一件 200 元以内的陶瓷杯',
  '手作陶瓷平时怎么保养？',
  '退换货流程是怎样的？'
]

const canSend = computed(() => input.value.trim().length > 0 && !streaming.value)

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

const GATED_TIP = {
  no_doc: '知识库里没有相关资料（该内容可能尚未收录）',
  low_score: '检索到的资料相关度不足，已选择拒答而非编造',
  domain_mismatch: '这个问题超出当前服务范围',
  empty_query: '问题为空',
  tool_error: '工具调用失败',
  timeout: '处理超时',
  rate_limited: '请求过于频繁',
  error: '服务异常'
}
function gatedTip(reason) {
  return GATED_TIP[reason] || '未能生成回答'
}

async function send(preset) {
  const content = (preset ?? input.value).trim()
  if (!content || streaming.value) return

  errorMsg.value = ''
  input.value = ''
  messages.value.push({ role: 'user', content })

  const reply = { role: 'assistant', content: '', gated: '' }
  messages.value.push(reply)

  streaming.value = true
  controller = new AbortController()
  await scrollToBottom()

  try {
    await chatStream({
      sessionId: sessionId.value,
      message: content,
      signal: controller.signal,
      onChunk: (chunk) => {
        reply.content += chunk
        scrollToBottom()
      },
      // 拒答原因挂到这条消息上，由模板渲染成一行浅色说明
      onGated: (reason) => {
        reply.gated = reason
      }
    })
  } catch (err) {
    // 用户主动「停止」会抛 AbortError，这不是错误
    if (err.name === 'AbortError') {
      if (!reply.content) reply.content = '（已停止生成）'
      return
    }
    errorMsg.value = err.message || '对话失败，请确认 ai-service 已启动'
    // 没收到任何内容就失败：移除空气泡，避免界面出现空白消息
    if (!reply.content) {
      messages.value = messages.value.filter((m) => m !== reply)
    }
  } finally {
    streaming.value = false
    controller = null
    await scrollToBottom()
  }
}

function stop() {
  controller?.abort()
}

function newSession() {
  stop()
  sessionId.value = resetSessionId()
  messages.value = []
  errorMsg.value = ''
}

/** Enter 发送、Shift + Enter 换行；isComposing 用于避开中文输入法的候选确认 */
function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}

/** 输入框随内容增高（上限交给 CSS 的 max-height） */
function autoResize(e) {
  const el = e.target
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 120)}px`
}

/* ---------------- 灵感生图 ---------------- */

async function generateImage() {
  const desc = imageDesc.value.trim()
  if (!desc || generating.value) return

  imageError.value = ''
  imageTask.value = { status: 'pending', message: '任务提交中…', image_url: null, positive_prompt: null, error: null }
  generating.value = true
  pollCancel = false

  try {
    const { task_id } = await requestImageTask(desc, usageType.value)
    await pollImageTask(task_id, 60) // 2s 一次，最多 60 次 ≈ 2 分钟
  } catch (err) {
    imageTask.value = null
    imageError.value = err.message || '任务提交失败，请确认 ai-service 已启动'
  } finally {
    generating.value = false
    stopPolling()
  }
}

async function pollImageTask(taskId, maxTimes) {
  for (let i = 0; i < maxTimes; i++) {
    // 已切走页面/组件卸载时停止轮询
    if (pollCancel) return
    const task = await getImageTaskResult(taskId)
    imageTask.value = task
    if (task.status === 'success' || task.status === 'failed') return
    await new Promise((r) => (pollTimer = setTimeout(r, 2000)))
  }
  imageError.value = '生成超时了，请稍后重试'
  imageTask.value = null
}

function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function resetImagePanel() {
  stopPolling()
  imageDesc.value = ''
  imageTask.value = null
  imageError.value = ''
}

/** 轮询取消标记：组件卸载 / 切换 Tab 时置位 */
let pollCancel = false

onBeforeUnmount(() => {
  controller?.abort()
  pollCancel = true
  stopPolling()
})
</script>

<template>
  <div class="ai container">
    <header class="ai-head">
      <div>
        <h1 class="title">巧见助手</h1>
        <p class="sub">问作品、问规则、问订单，也可以让 AI 画点灵感</p>
      </div>
      <div class="head-actions">
        <div class="tabs" role="tablist">
          <button
            :class="['tab', { 'tab--active': activeTab === 'chat' }]"
            role="tab"
            @click="activeTab = 'chat'"
          >
            对话
          </button>
          <button
            :class="['tab', { 'tab--active': activeTab === 'image' }]"
            role="tab"
            @click="activeTab = 'image'"
          >
            灵感生图
          </button>
        </div>
        <button v-if="activeTab === 'chat'" class="btn btn-ghost new-btn" @click="newSession">
          新会话
        </button>
        <button v-else class="btn btn-ghost new-btn" @click="resetImagePanel">清空</button>
      </div>
    </header>

    <!-- ================= 灵感生图面板 ================= -->
    <div v-if="activeTab === 'image'" class="chat img-panel">
      <div class="img-body">
        <div class="img-form">
          <label class="field-label">描述你想要的画面</label>
          <textarea
            v-model="imageDesc"
            rows="4"
            placeholder="例如：一只手作青瓷茶杯，放在原木桌面上，晨光从窗户斜照进来，画面安静温暖…"
          ></textarea>

          <label class="field-label">用途（AI 生成图不可用作商品实物主图）</label>
          <div class="usage-group">
            <button
              v-for="opt in USAGE_OPTIONS"
              :key="opt.value"
              :class="['usage-chip', { 'usage-chip--active': usageType === opt.value }]"
              @click="usageType = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>

          <button class="btn btn-primary gen-btn" :disabled="!canGenerate" @click="generateImage">
            {{ generating ? '生成中…' : '生成灵感图' }}
          </button>
        </div>

        <div class="img-result">
          <p v-if="imageError" class="error">{{ imageError }}</p>

          <template v-if="imageTask">
            <div v-if="imageTask.status !== 'success'" class="img-loading">
              <span class="typing"><i></i><i></i><i></i></span>
              <p>{{ imageTask.message || '正在生成，大约需要 10~30 秒…' }}</p>
            </div>

            <figure v-else class="img-figure">
              <img :src="imageTask.image_url" alt="AI 生成灵感图" />
              <figcaption class="ai-tag">AI 生成，仅供参考</figcaption>
              <p v-if="imageTask.positive_prompt" class="img-prompt">
                {{ imageTask.positive_prompt }}
              </p>
            </figure>
          </template>

          <div v-else-if="!imageError" class="img-empty">
            <p>描述一段画面，让 AI 帮你出创作灵感图。</p>
            <p class="img-empty-sub">生成结果带有「AI 生成」水印与元数据标识。</p>
          </div>
        </div>
      </div>

      <p class="disclaimer">
        AI 生成内容仅供参考，不构成商品实物展示；请勿将生成图用作商品主图。
      </p>
    </div>

    <!-- ================= 对话面板 ================= -->
    <div v-else class="chat">
      <div ref="listRef" class="stream">
        <!-- 空状态：给几个真实可问的问题，降低「不知道能问什么」的门槛 -->
        <div v-if="!messages.length" class="empty">
          <p class="empty-title">想了解点什么？</p>
          <div class="suggestions">
            <button
              v-for="s in SUGGESTIONS"
              :key="s"
              class="suggestion"
              @click="send(s)"
            >
              {{ s }}
            </button>
          </div>
        </div>

        <div
          v-for="(m, i) in messages"
          :key="i"
          :class="['msg', `msg--${m.role}`]"
        >
          <div class="avatar">{{ m.role === 'user' ? '我' : '巧' }}</div>
          <div class="bubble">
            <template v-if="m.content">{{ m.content }}</template>
            <!-- 拒答时 content 可能为空，此时不能再显示"正在输入"的三点动画 -->
            <span v-else-if="!m.gated" class="typing"><i></i><i></i><i></i></span>
            <p v-if="m.gated" class="gated-tip">{{ gatedTip(m.gated) }}</p>
          </div>
        </div>
      </div>

      <p v-if="errorMsg" class="error">{{ errorMsg }}</p>

      <div class="composer">
        <textarea
          v-model="input"
          rows="1"
          placeholder="说点什么…（Enter 发送，Shift + Enter 换行）"
          @input="autoResize"
          @keydown="onKeydown"
        ></textarea>
        <button v-if="streaming" class="btn btn-ghost" @click="stop">停止</button>
        <button v-else class="btn btn-primary" :disabled="!canSend" @click="send()">
          发送
        </button>
      </div>

      <p class="disclaimer">
        AI 生成内容仅供参考，商品价格与库存请以商品页为准。
      </p>
    </div>
  </div>
</template>

<style scoped>
.ai {
  padding-top: 52px;
  padding-bottom: 60px;
}

.ai-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 26px;
}
.title {
  font-family: var(--font-serif);
  font-size: 30px;
  font-weight: 500;
  letter-spacing: 0.2em;
}
.sub {
  margin-top: 8px;
  font-size: 13.5px;
  color: var(--c-text-mute);
  letter-spacing: 0.08em;
}
.new-btn {
  height: 38px;
  padding: 0 20px;
  font-size: 13.5px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ---------- Tab 切换 ---------- */
.tabs {
  display: flex;
  background: var(--c-bg-deep);
  border-radius: var(--r-pill);
  padding: 3px;
}
.tab {
  padding: 7px 18px;
  font-size: 13.5px;
  letter-spacing: 0.06em;
  color: var(--c-text-sub);
  border-radius: var(--r-pill);
  transition: all 0.25s var(--ease);
}
.tab--active {
  background: var(--c-surface);
  color: var(--c-accent-deep);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

/* ---------- 生图面板 ---------- */
.img-panel {
  height: auto;
  min-height: 420px;
}
.img-body {
  display: grid;
  grid-template-columns: 5fr 7fr;
  gap: 28px;
  padding: 28px;
}
.field-label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--c-text-sub);
  letter-spacing: 0.05em;
}
.img-form textarea {
  width: 100%;
  resize: vertical;
  min-height: 96px;
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.6;
  background: var(--c-bg-deep);
  border: 1px solid transparent;
  border-radius: var(--r-md);
  transition: border-color 0.25s var(--ease), background 0.25s var(--ease);
}
.img-form textarea:focus {
  background: var(--c-surface);
  border-color: var(--c-accent);
}
.usage-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}
.usage-chip {
  padding: 7px 16px;
  font-size: 13px;
  color: var(--c-text-sub);
  background: var(--c-bg-deep);
  border: 1px solid transparent;
  border-radius: var(--r-pill);
  transition: all 0.25s var(--ease);
}
.usage-chip--active {
  color: var(--c-accent-deep);
  background: var(--c-accent-soft);
  border-color: var(--c-accent);
}
.gen-btn {
  width: 100%;
  height: 44px;
  font-size: 14.5px;
  letter-spacing: 0.1em;
}

.img-result {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--c-bg-deep);
  border-radius: var(--r-md);
  padding: 20px;
  min-height: 300px;
}
.img-empty {
  text-align: center;
  font-size: 14px;
  color: var(--c-text-sub);
  line-height: 2;
}
.img-empty-sub {
  font-size: 12.5px;
  color: var(--c-text-mute);
}
.img-loading {
  text-align: center;
  color: var(--c-text-sub);
  font-size: 13.5px;
}
.img-loading .typing {
  height: 26px;
  margin-bottom: 10px;
}
.img-figure {
  width: 100%;
  text-align: center;
}
.img-figure img {
  max-width: 100%;
  max-height: 380px;
  border-radius: var(--r-md);
  box-shadow: var(--sh-card);
}
.ai-tag {
  display: inline-block;
  margin-top: 12px;
  padding: 4px 14px;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--c-text-sub);
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--r-pill);
}
.img-prompt {
  margin-top: 10px;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--c-text-mute);
  max-width: 480px;
  margin-left: auto;
  margin-right: auto;
}

.chat {
  display: flex;
  flex-direction: column;
  height: min(66vh, 620px);
  background: var(--c-surface);
  border-radius: var(--r-lg);
  box-shadow: var(--sh-card);
  overflow: hidden;
}

.stream {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ---------- 空状态 ---------- */
.empty {
  margin: auto;
  text-align: center;
}
.empty-title {
  font-family: var(--font-serif);
  font-size: 17px;
  letter-spacing: 0.12em;
  color: var(--c-text-sub);
  margin-bottom: 18px;
}
.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 540px;
}
.suggestion {
  padding: 8px 18px;
  font-size: 13.5px;
  color: var(--c-text-sub);
  background: var(--c-bg-deep);
  border-radius: var(--r-pill);
  transition: all 0.28s var(--ease);
}
.suggestion:hover {
  color: var(--c-accent-deep);
  background: var(--c-accent-soft);
}

/* ---------- 消息 ---------- */
.msg {
  display: flex;
  gap: 10px;
  max-width: 82%;
}
.msg--user {
  flex-direction: row-reverse;
  margin-left: auto;
}
.avatar {
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12.5px;
  font-family: var(--font-serif);
  user-select: none;
}
.msg--assistant .avatar {
  background: var(--c-accent-soft);
  color: var(--c-accent-deep);
}
.msg--user .avatar {
  background: var(--c-bg-deep);
  color: var(--c-text-sub);
}

.bubble {
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 14.5px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg--assistant .bubble {
  background: var(--c-bg-deep);
  color: var(--c-text);
  border-top-left-radius: 4px;
}
.msg--user .bubble {
  background: var(--c-accent);
  color: #fff;
  border-top-right-radius: 4px;
}

/* 等待首个 token 时的三点动画 */
/* 拒答原因说明：比正文弱一档，不抢注意力，但要能看见 */
.gated-tip {
  margin: 8px 0 0;
  padding-top: 8px;
  border-top: 1px dashed rgba(0, 0, 0, 0.1);
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--c-text-mute);
}
.typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 20px;
}
.typing i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--c-text-mute);
  animation: blink 1.2s infinite;
}
.typing i:nth-child(2) {
  animation-delay: 0.2s;
}
.typing i:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%,
  60%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

.error {
  padding: 10px 24px 0;
  font-size: 13px;
  color: var(--c-danger);
}

/* ---------- 输入区 ---------- */
.composer {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--c-line);
}
.composer textarea {
  flex: 1;
  resize: none;
  min-height: 44px;
  max-height: 120px;
  padding: 12px 16px;
  font-size: 14.5px;
  line-height: 1.5;
  background: var(--c-bg-deep);
  border: 1px solid transparent;
  border-radius: var(--r-md);
  transition: border-color 0.25s var(--ease), background 0.25s var(--ease);
}
.composer textarea:focus {
  background: var(--c-surface);
  border-color: var(--c-accent);
}
.composer .btn {
  height: 44px;
  padding: 0 22px;
  flex: none;
}

.disclaimer {
  padding: 0 24px 16px;
  font-size: 12px;
  color: var(--c-text-mute);
}

@media (max-width: 640px) {
  .chat {
    height: calc(100vh - 210px);
  }
  .img-panel {
    height: auto;
  }
  .img-body {
    grid-template-columns: 1fr;
    padding: 18px;
  }
  .msg {
    max-width: 94%;
  }
  .ai-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  .head-actions {
    width: 100%;
    justify-content: space-between;
  }
  .title {
    font-size: 25px;
  }
}
</style>
