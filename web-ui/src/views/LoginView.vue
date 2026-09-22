<script setup>
import { ref, reactive, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { userApi } from '@/api/modules'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** login=登录 / register=注册 */
const mode = ref('login')
/** password=账号密码 / phone=手机验证码 */
const way = ref('password')

const pwdForm = reactive({ username: '', password: '' })
const phoneForm = reactive({ phone: '', code: '' })
const regForm = reactive({ username: '', phone: '', password: '', confirm: '' })

const loading = ref(false)
const errorMsg = ref('')
const tipMsg = ref('')
/** 验证码倒计时（秒） */
const seconds = ref(0)
let timer = null

/** 登录成功后的回跳地址：只接受站内路径，防止开放重定向 */
const redirect = computed(() => {
  const raw = route.query.redirect
  return typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//')
    ? raw
    : '/products'
})

function resetMsg() {
  errorMsg.value = ''
  tipMsg.value = ''
}

function isPhone(p) {
  return /^1[3-9]\d{9}$/.test(p)
}

function switchWay(next) {
  if (way.value === next) return
  way.value = next
  resetMsg()
}

function toRegister() {
  mode.value = 'register'
  resetMsg()
}

function toLogin() {
  mode.value = 'login'
  resetMsg()
}

/* ---------------- 账号密码登录 ---------------- */
async function onPasswordLogin() {
  resetMsg()
  if (!pwdForm.username.trim()) return (errorMsg.value = '请输入用户名')
  if (!pwdForm.password) return (errorMsg.value = '请输入密码')

  loading.value = true
  try {
    await userStore.login({
      username: pwdForm.username.trim(),
      password: pwdForm.password
    })
    router.replace(redirect.value)
  } catch (e) {
    errorMsg.value = e.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/* ---------------- 手机验证码 ---------------- */
async function sendCode() {
  resetMsg()
  if (!isPhone(phoneForm.phone)) return (errorMsg.value = '请输入正确的手机号')
  if (seconds.value > 0) return

  loading.value = true
  try {
    await userApi.sendSms(phoneForm.phone)
    tipMsg.value = '验证码已发送（开发环境请查看 user-service 控制台输出的 6 位数字）'
    startCountdown()
  } catch (e) {
    errorMsg.value = e.message || '验证码发送失败'
  } finally {
    loading.value = false
  }
}

function startCountdown() {
  seconds.value = 60
  timer = setInterval(() => {
    seconds.value -= 1
    if (seconds.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function onPhoneLogin() {
  resetMsg()
  if (!isPhone(phoneForm.phone)) return (errorMsg.value = '请输入正确的手机号')
  if (!phoneForm.code.trim()) return (errorMsg.value = '请输入验证码')

  loading.value = true
  try {
    await userStore.loginByPhone({
      phone: phoneForm.phone,
      code: phoneForm.code.trim()
    })
    router.replace(redirect.value)
  } catch (e) {
    errorMsg.value = e.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/* ---------------- 注册 ---------------- */
async function onRegister() {
  resetMsg()
  if (!regForm.username.trim()) return (errorMsg.value = '请输入用户名')
  if (!isPhone(regForm.phone)) return (errorMsg.value = '请输入正确的手机号')
  if (regForm.password.length < 6) return (errorMsg.value = '密码至少 6 位')
  if (regForm.password !== regForm.confirm) return (errorMsg.value = '两次输入的密码不一致')

  loading.value = true
  try {
    await userStore.register({
      username: regForm.username.trim(),
      phone: regForm.phone,
      password: regForm.password
    })

    if (userStore.isLogin) {
      router.replace(redirect.value)
    } else {
      // 后端注册接口未下发有效 token 时的兜底：引导用户手动登录
      mode.value = 'login'
      way.value = 'password'
      pwdForm.username = regForm.username.trim()
      pwdForm.password = ''
      tipMsg.value = '注册成功，请使用刚设置的密码登录'
    }
  } catch (e) {
    errorMsg.value = e.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="auth">
    <!-- ==================== 左：视觉 ==================== -->
    <aside class="auth-visual">
      <img class="visual-img" src="/products/hero.png" alt="巧见 · 手作市集" />
      <div class="visual-veil"></div>
      <div class="visual-copy">
        <p class="visual-kicker">巧见 · 手作市集</p>
        <h2 class="visual-title">
          把慢下来的时光<br />
          酿成手作的形状
        </h2>
        <p class="visual-sub">
          登录之后，你可以收藏喜欢的手作、<br />
          把它们放进购物车，再带回家。
        </p>
      </div>
    </aside>

    <!-- ==================== 右：表单 ==================== -->
    <section class="auth-panel">
      <div class="panel-inner">
        <header class="panel-head">
          <p class="panel-kicker">{{ mode === 'login' ? 'WELCOME BACK' : 'JOIN US' }}</p>
          <h1 class="panel-title">{{ mode === 'login' ? '登录' : '注册' }}</h1>
          <p class="panel-sub">
            {{ mode === 'login' ? '欢迎回来，继续你的巧见之旅' : '创建账号，开始收集你的手作' }}
          </p>
        </header>

        <!-- 登录方式切换 -->
        <div v-if="mode === 'login'" class="tabs">
          <button
            :class="['tab', { active: way === 'password' }]"
            type="button"
            @click="switchWay('password')"
          >
            账号密码
          </button>
          <button
            :class="['tab', { active: way === 'phone' }]"
            type="button"
            @click="switchWay('phone')"
          >
            手机验证码
          </button>
        </div>

        <p v-if="errorMsg" class="msg msg-error">{{ errorMsg }}</p>
        <p v-if="tipMsg" class="msg msg-tip">{{ tipMsg }}</p>

        <!-- 密码登录 -->
        <form
          v-if="mode === 'login' && way === 'password'"
          class="form"
          @submit.prevent="onPasswordLogin"
        >
          <label class="field">
            <span class="field-label">用户名</span>
            <input
              v-model="pwdForm.username"
              type="text"
              autocomplete="username"
              placeholder="请输入用户名"
            />
          </label>
          <label class="field">
            <span class="field-label">密码</span>
            <input
              v-model="pwdForm.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </label>
          <button class="btn btn-primary submit" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登 录' }}
          </button>
        </form>

        <!-- 验证码登录 -->
        <form
          v-else-if="mode === 'login'"
          class="form"
          @submit.prevent="onPhoneLogin"
        >
          <label class="field">
            <span class="field-label">手机号</span>
            <input
              v-model="phoneForm.phone"
              type="tel"
              maxlength="11"
              autocomplete="tel"
              placeholder="请输入手机号"
            />
          </label>
          <label class="field">
            <span class="field-label">验证码</span>
            <div class="field-row">
              <input
                v-model="phoneForm.code"
                type="text"
                maxlength="6"
                inputmode="numeric"
                placeholder="6 位数字验证码"
              />
              <button
                class="btn btn-ghost code-btn"
                type="button"
                :disabled="seconds > 0 || loading"
                @click="sendCode"
              >
                {{ seconds > 0 ? `${seconds}s 后重发` : '获取验证码' }}
              </button>
            </div>
          </label>
          <button class="btn btn-primary submit" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登 录' }}
          </button>
        </form>

        <!-- 注册 -->
        <form v-else class="form" @submit.prevent="onRegister">
          <label class="field">
            <span class="field-label">用户名</span>
            <input v-model="regForm.username" type="text" placeholder="设置一个用户名" />
          </label>
          <label class="field">
            <span class="field-label">手机号</span>
            <input v-model="regForm.phone" type="tel" maxlength="11" placeholder="请输入手机号" />
          </label>
          <label class="field">
            <span class="field-label">密码</span>
            <input v-model="regForm.password" type="password" placeholder="至少 6 位" />
          </label>
          <label class="field">
            <span class="field-label">确认密码</span>
            <input v-model="regForm.confirm" type="password" placeholder="再次输入密码" />
          </label>
          <button class="btn btn-primary submit" type="submit" :disabled="loading">
            {{ loading ? '注册中…' : '注册并登录' }}
          </button>
        </form>

        <p class="switch">
          <template v-if="mode === 'login'">
            还没有账号？
            <button class="link" type="button" @click="toRegister">立即注册</button>
          </template>
          <template v-else>
            已经有账号了？
            <button class="link" type="button" @click="toLogin">去登录</button>
          </template>
        </p>

        <p class="back">
          <router-link class="link" to="/products">← 先去市集逛逛</router-link>
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: minmax(0, 44%) minmax(0, 56%);
  min-height: calc(100vh - var(--nav-h));
}

/* ==================== 左侧视觉 ==================== */
.auth-visual {
  position: relative;
  overflow: hidden;
}
.visual-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  animation: kenburns 26s ease-in-out infinite alternate;
}
@keyframes kenburns {
  from {
    transform: scale(1.04);
  }
  to {
    transform: scale(1.14) translate3d(-1.5%, -1%, 0);
  }
}
.visual-veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(51, 51, 51, 0.16) 0%,
    rgba(51, 51, 51, 0.34) 55%,
    rgba(51, 51, 51, 0.62) 100%
  );
}
.visual-copy {
  position: absolute;
  left: 44px;
  right: 44px;
  bottom: 52px;
  color: #fff;
  z-index: 2;
}
.visual-kicker {
  font-size: 12.5px;
  letter-spacing: 0.34em;
  color: rgba(255, 255, 255, 0.86);
}
.visual-title {
  margin-top: 16px;
  font-family: var(--font-serif);
  font-size: clamp(24px, 2.4vw, 34px);
  font-weight: 500;
  line-height: 1.5;
  letter-spacing: 0.08em;
}
.visual-sub {
  margin-top: 16px;
  font-size: 13.5px;
  line-height: 2;
  color: rgba(255, 255, 255, 0.82);
}

/* ==================== 右侧表单 ==================== */
.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px 24px;
}
.panel-inner {
  width: 100%;
  max-width: 380px;
}

.panel-kicker {
  font-size: 12px;
  letter-spacing: 0.32em;
  color: var(--c-accent-deep);
}
.panel-title {
  margin-top: 12px;
  font-family: var(--font-serif);
  font-size: 30px;
  font-weight: 500;
  letter-spacing: 0.18em;
}
.panel-sub {
  margin-top: 10px;
  font-size: 13.5px;
  color: var(--c-text-mute);
  letter-spacing: 0.04em;
}

/* 顶部 Tab */
.tabs {
  display: flex;
  gap: 26px;
  margin-top: 32px;
  border-bottom: 1px solid var(--c-line);
}
.tab {
  position: relative;
  padding: 0 2px 12px;
  font-size: 14.5px;
  color: var(--c-text-mute);
  transition: color 0.25s var(--ease);
}
.tab:hover {
  color: var(--c-text);
}
.tab.active {
  color: var(--c-text);
}
.tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--c-accent);
}

/* 消息提示 */
.msg {
  margin-top: 18px;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.7;
  border-radius: var(--r-md);
}
.msg-error {
  color: var(--c-danger);
  background: #fdf1ef;
}
.msg-tip {
  color: var(--c-success);
  background: #eef6f1;
}

/* 表单 */
.form {
  margin-top: 26px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.field-label {
  font-size: 12.5px;
  letter-spacing: 0.12em;
  color: var(--c-text-sub);
}
.field input {
  height: 46px;
  padding: 0 16px;
  font-size: 14.5px;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--r-md);
  transition: border-color 0.25s var(--ease), box-shadow 0.25s var(--ease);
}
.field input::placeholder {
  color: var(--c-text-mute);
}
.field input:focus {
  border-color: var(--c-accent);
  box-shadow: 0 0 0 3px rgba(62, 143, 176, 0.16);
}

.field-row {
  display: flex;
  gap: 10px;
}
.field-row input {
  flex: 1;
}
.code-btn {
  height: 46px;
  padding: 0 16px;
  font-size: 13.5px;
  flex-shrink: 0;
  white-space: nowrap;
}

.submit {
  margin-top: 6px;
  width: 100%;
  height: 48px;
  letter-spacing: 0.2em;
}

.switch {
  margin-top: 22px;
  font-size: 13.5px;
  color: var(--c-text-sub);
  text-align: center;
}
.link {
  color: var(--c-accent-deep);
  transition: color 0.25s var(--ease);
}
.link:hover {
  color: var(--c-accent);
  text-decoration: underline;
}

.back {
  margin-top: 30px;
  text-align: center;
  font-size: 13px;
}

/* ==================== 响应式 ==================== */
@media (max-width: 900px) {
  .auth {
    grid-template-columns: 1fr;
    min-height: auto;
  }
  .auth-visual {
    height: 210px;
  }
  .visual-copy {
    left: 24px;
    right: 24px;
    bottom: 22px;
  }
  .visual-sub {
    display: none;
  }
  .visual-title {
    font-size: 21px;
  }
  .auth-panel {
    padding: 42px 24px 64px;
  }
}
</style>
