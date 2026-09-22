<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cartApi, accountApi, rechargeApi } from '@/api/modules'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const FALLBACK_IMG = '/products/hero.png'
const PHONE_RE = /^1[3-9]\d{9}$/

/** 待结算明细（来自购物车勾选项） */
const items = ref([])
const loading = ref(true)
const errorMsg = ref('')

/** 账户可用余额 */
const balance = ref(null)
const balanceLoaded = ref(false)

const form = ref({ receiverName: '', receiverPhone: '', receiverAddress: '' })
const errors = ref({})

const submitting = ref(false)
/** 下单成功后的订单结果（B2C 自营：一次结算一张订单） */
const result = ref(null)

const rechargeOpen = ref(false)
const rechargeAmount = ref(null)
const recharging = ref(false)

const toast = ref({ show: false, type: 'success', text: '' })
let toastTimer = null

const STATUS_TEXT = {
  PENDING: '待付款',
  FROZEN: '已付款 · 待发货',
  SHIPPED: '已发货',
  RECEIVED: '已收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDED: '已退款'
}

const totalAmount = computed(() =>
  items.value.reduce((sum, i) => sum + Number(i.unitPrice || 0) * Number(i.quantity || 0), 0)
)
const balanceValue = computed(() => (balance.value === null ? 0 : Number(balance.value)))
const balanceEnough = computed(() => balanceValue.value >= totalAmount.value)
const shortfall = computed(() => Math.max(0, totalAmount.value - balanceValue.value))

function showToast(type, text) {
  toast.value = { show: true, type, text }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value.show = false), 2600)
}

/** 读取购物车勾选结果（cartId 列表） */
function readCheckedCartIds() {
  try {
    const raw = sessionStorage.getItem('checkout_cart_ids')
    const arr = raw ? JSON.parse(raw) : []
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await cartApi.list(userStore.userId)
    const list = Array.isArray(res.data) ? res.data : []
    const checkedIds = readCheckedCartIds()
    const valid = list.filter((i) => !i.invalid)
    // 有勾选信息则按勾选过滤；否则结算购物车中全部有效项
    items.value = checkedIds.length
      ? valid.filter((i) => checkedIds.includes(i.cartId))
      : valid
  } catch (e) {
    errorMsg.value = e.message || '结算信息加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadBalance() {
  try {
    const res = await accountApi.balance(userStore.userId)
    balance.value = Number(res.data ?? 0)
  } catch {
    // 余额拉取失败不阻塞页面，提交时会再由后端兜底校验
    balance.value = null
  } finally {
    balanceLoaded.value = true
  }
}

function validate() {
  const next = {}
  if (!form.value.receiverName.trim()) next.receiverName = '请填写收货人姓名'
  if (!PHONE_RE.test(form.value.receiverPhone.trim())) next.receiverPhone = '请填写正确的手机号'
  if (form.value.receiverAddress.trim().length < 5) next.receiverAddress = '请填写详细收货地址'
  errors.value = next
  return Object.keys(next).length === 0
}

async function submitOrder() {
  if (submitting.value || !items.value.length) return
  if (!validate()) {
    showToast('error', '请完善收货信息')
    return
  }
  if (!balanceEnough.value) {
    showToast('error', '余额不足，请先充值')
    rechargeOpen.value = true
    return
  }

  submitting.value = true
  try {
    const res = await cartApi.checkout({
      userId: userStore.userId,
      cartIds: items.value.map((i) => i.cartId),
      receiverName: form.value.receiverName.trim(),
      receiverPhone: form.value.receiverPhone.trim(),
      receiverAddress: form.value.receiverAddress.trim()
    })
    result.value = Array.isArray(res.data) ? res.data : []
    sessionStorage.removeItem('checkout_cart_ids')
    // 资金已冻结，刷新余额以保持一致
    loadBalance()
    showToast('success', '下单成功，资金已冻结在平台')
  } catch (e) {
    showToast('error', e.message || '下单失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function openRecharge() {
  rechargeAmount.value = Math.max(100, Math.ceil(shortfall.value / 100) * 100 || 100)
  rechargeOpen.value = true
}

async function doRecharge() {
  const amount = Number(rechargeAmount.value)
  if (!amount || amount <= 0) {
    showToast('error', '请输入正确的充值金额')
    return
  }
  recharging.value = true
  try {
    const res = await rechargeApi.create({ userId: userStore.userId, amount })
    const { rechargeNo, payUrl } = res.data || {}
    if (!payUrl) {
      showToast('error', '获取支付链接失败')
      return
    }

    rechargeOpen.value = false
    window.open(payUrl, '_blank')
    startPolling(rechargeNo)
  } catch (e) {
    showToast('error', e.message || '发起充值失败')
  } finally {
    recharging.value = false
  }
}

/** 单次查询，返回 true 表示这笔单已到终态，不用再轮询了 */
async function pollOnce(rechargeNo) {
  const res = await rechargeApi.status({ rechargeNo, userId: userStore.userId })
  const status = res.data?.status
  if (status === 'PAID') {
    await loadBalance()
    showToast('success', '充值成功')
    return true
  }
  if (status === 'CLOSED') {
    showToast('error', '该笔充值已关闭')
    return true
  }
  return false
}

/** 轮询充值状态：最多 3 分钟，付完即停 */
let pollTimer = null
function startPolling(rechargeNo) {
  stopPolling()
  let times = 0
  const tick = async () => {
    times += 1
    // 3 分钟后放弃（60 次 × 3 秒），避免用户没付就一直轮询
    if (times > 60) {
      stopPolling()
      showToast('error', '充值超时，如已付款请刷新页面查看余额')
      return
    }
    try {
      if (await pollOnce(rechargeNo)) stopPolling()
    } catch {
      // 轮询失败不打断，下个周期继续
    }
  }

  tick()
  pollTimer = setInterval(tick, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function goOrders() {
  router.push({ name: 'orders' })
}

onMounted(() => {
  load()
  loadBalance()

  const backNo = route.query.out_trade_no
  if (backNo) startPolling(String(backNo))
})
onUnmounted(() => {
  if (toastTimer) clearTimeout(toastTimer)
  // 组件销毁时必须停掉轮询，否则页面跳转后定时器还在跑，
  // 会持续请求一个已不存在的页面上下文（也是内存泄漏）
  stopPolling()
})
</script>

<template>
  <div class="checkout container">
    <!-- 面包屑 -->
    <nav class="crumb">
      <router-link to="/cart">购物车</router-link>
      <span class="crumb-sep">/</span>
      <span class="crumb-cur">确认订单</span>
    </nav>

    <h1 class="title">确认订单</h1>

    <!-- 加载中 -->
    <div v-if="loading" class="state">
      <div class="spinner"></div>
      <p>正在准备订单…</p>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="errorMsg" class="state state-error">
      <p class="state-title">结算信息没能加载</p>
      <p class="state-desc">{{ errorMsg }}</p>
      <div class="state-actions">
        <button class="btn btn-primary" @click="load">重试</button>
        <router-link class="btn btn-ghost" to="/cart">返回购物车</router-link>
      </div>
    </div>

    <!-- 空：没有可结算项 -->
    <div v-else-if="!items.length" class="state">
      <p class="state-title">没有待结算的作品</p>
      <p class="state-desc">先去购物车勾选要购买的作品吧</p>
      <div class="state-actions">
        <router-link class="btn btn-primary" to="/cart">返回购物车</router-link>
      </div>
    </div>

    <!-- 下单成功 -->
    <template v-else-if="result">
      <div class="done card">
        <p class="done-icon">✓</p>
        <h2 class="done-title">下单成功，资金已冻结在平台</h2>
        <p class="done-sub">确认收货后平台才会完成本次交易结算</p>

        <ul class="done-list">
          <li v-for="o in result" :key="o.id">
            <span class="done-no">订单号 {{ o.orderNo }}</span>
            <span class="done-amt price">{{ Number(o.totalAmount || 0).toFixed(2) }}</span>
            <span class="done-status">{{ STATUS_TEXT[o.status] || o.status }}</span>
          </li>
        </ul>

        <div class="done-actions">
          <button class="btn btn-primary" @click="goOrders">查看我的订单</button>
          <router-link class="btn btn-ghost" to="/products">继续逛市集</router-link>
        </div>
      </div>
    </template>

    <!-- 结算主体 -->
    <template v-else>
      <div class="co-main">
        <!-- 左：收货信息 + 明细 -->
        <div class="col-left">
          <section class="card block">
            <h2 class="block-title">收货信息</h2>
            <div class="field">
              <label>收货人</label>
              <input v-model="form.receiverName" type="text" placeholder="请输入姓名" />
              <p v-if="errors.receiverName" class="field-err">{{ errors.receiverName }}</p>
            </div>
            <div class="field">
              <label>手机号</label>
              <input v-model="form.receiverPhone" type="text" inputmode="numeric" placeholder="11 位手机号" />
              <p v-if="errors.receiverPhone" class="field-err">{{ errors.receiverPhone }}</p>
            </div>
            <div class="field">
              <label>收货地址</label>
              <textarea v-model="form.receiverAddress" rows="3" placeholder="省 / 市 / 区 街道门牌"></textarea>
              <p v-if="errors.receiverAddress" class="field-err">{{ errors.receiverAddress }}</p>
            </div>
          </section>

          <section class="card block">
            <h2 class="block-title">作品清单</h2>
            <ul class="goods">
              <li v-for="i in items" :key="i.cartId" class="good">
                <img
                  class="good-img"
                  :src="i.coverImage || FALLBACK_IMG"
                  :alt="i.productName"
                  @error="(e) => (e.target.src = FALLBACK_IMG)"
                />
                <div class="good-meta">
                  <p class="good-name">{{ i.productName || '未知作品' }}</p>
                  <p class="good-unit">
                    单价 <span class="price price-sm">{{ Number(i.unitPrice || 0).toFixed(2) }}</span>
                    × {{ i.quantity }}
                  </p>
                </div>
                <p class="good-sub price">
                  {{ (Number(i.unitPrice || 0) * Number(i.quantity || 0)).toFixed(2) }}
                </p>
              </li>
            </ul>
          </section>
        </div>

        <!-- 右：付款 -->
        <aside class="col-right card pay">
          <h2 class="block-title">付款信息</h2>

          <div class="pay-row">
            <span>可用余额</span>
            <span v-if="balanceLoaded" class="wallet" :class="{ low: !balanceEnough }">
              {{ balanceValue.toFixed(2) }}
              <button class="recharge-link" @click="openRecharge">充值</button>
            </span>
            <span v-else class="wallet">加载中…</span>
          </div>

          <div class="pay-row">
            <span>商品金额</span>
            <span class="price">{{ totalAmount.toFixed(2) }}</span>
          </div>

          <div class="pay-row total">
            <span>应付合计</span>
            <span class="price price-lg">{{ totalAmount.toFixed(2) }}</span>
          </div>

          <p v-if="balanceLoaded && !balanceEnough" class="short">
            还差 {{ shortfall.toFixed(2) }}，请先充值
          </p>

          <button
            class="btn btn-primary submit"
            :disabled="submitting || !balanceEnough"
            @click="submitOrder"
          >
            {{ submitting ? '提交中…' : '提交订单' }}
          </button>

          <p class="pay-tip">资金将冻结在平台，确认收货前不会结算给商家</p>
        </aside>
      </div>
    </template>

    <!-- 充值弹层 -->
    <transition name="fade">
      <div v-if="rechargeOpen" class="mask" @click.self="rechargeOpen = false">
        <div class="modal card">
          <h3 class="modal-title">账户充值</h3>
          <p class="modal-sub">将跳转支付宝沙箱完成支付，付款后余额自动到账</p>

          <div class="quick">
            <button v-for="a in [100, 200, 500, 1000]" :key="a" @click="rechargeAmount = a">
              ¥{{ a }}
            </button>
          </div>

          <input v-model="rechargeAmount" type="number" min="1" class="modal-input" placeholder="输入充值金额" />

          <div class="modal-actions">
            <button class="btn btn-ghost" @click="rechargeOpen = false">取消</button>
            <button class="btn btn-primary" :disabled="recharging" @click="doRecharge">
              {{ recharging ? '充值中…' : '确认充值' }}
            </button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 结果提示 -->
    <transition name="toast">
      <div v-if="toast.show" :class="['toast', `toast-${toast.type}`]">
        {{ toast.text }}
      </div>
    </transition>
  </div>
</template>

<style scoped>
.checkout {
  padding-top: 40px;
  padding-bottom: 90px;
}

/* ---------- 面包屑 ---------- */
.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--c-text-mute);
  margin-bottom: 20px;
}
.crumb a:hover {
  color: var(--c-accent);
}
.crumb-sep {
  color: var(--c-line);
}
.crumb-cur {
  color: var(--c-text-sub);
}

.title {
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 500;
  letter-spacing: 0.18em;
  margin-bottom: 28px;
}

/* ---------- 状态占位 ---------- */
.state {
  padding: 80px 0;
  text-align: center;
  color: var(--c-text-mute);
  font-size: 14px;
}
.spinner {
  width: 26px;
  height: 26px;
  margin: 0 auto 18px;
  border: 2.5px solid var(--c-line);
  border-top-color: var(--c-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.state-title {
  font-family: var(--font-serif);
  font-size: 20px;
  letter-spacing: 0.1em;
  color: var(--c-text);
}
.state-desc {
  margin-top: 10px;
  font-size: 13.5px;
  color: var(--c-text-sub);
}
.state-actions {
  margin-top: 26px;
  display: flex;
  gap: 14px;
  justify-content: center;
  flex-wrap: wrap;
}

/* ---------- 两栏 ---------- */
.co-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 28px;
  align-items: start;
}
.col-left {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.block {
  padding: 24px 26px;
}
.block-title {
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 500;
  letter-spacing: 0.12em;
  margin-bottom: 18px;
}

/* ---------- 表单 ---------- */
.field {
  margin-bottom: 16px;
}
.field label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--c-text-sub);
  letter-spacing: 0.06em;
}
.field input,
.field textarea {
  width: 100%;
  padding: 11px 14px;
  font-size: 14px;
  color: var(--c-text);
  background: var(--c-bg);
  border: 1px solid var(--c-line);
  border-radius: var(--r-sm);
  transition: border-color 0.25s var(--ease), box-shadow 0.25s var(--ease);
  resize: vertical;
}
.field input:focus,
.field textarea:focus {
  outline: none;
  border-color: var(--c-accent);
  box-shadow: 0 0 0 3px var(--c-accent-soft);
}
.field-err {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--c-danger);
}

/* ---------- 清单 ---------- */
.goods {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.good {
  display: grid;
  grid-template-columns: 60px minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
}
.good-img {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: var(--r-sm);
  background: var(--c-bg);
}
.good-name {
  font-size: 14.5px;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.good-unit {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--c-text-mute);
}
.good-sub {
  font-size: 15px;
}

.price {
  color: var(--c-accent-deep);
  font-weight: 600;
}
.price-sm {
  font-size: 13px;
}
.price-lg {
  font-size: 22px;
}

/* ---------- 付款侧栏 ---------- */
.pay {
  position: sticky;
  top: calc(var(--nav-h) + 20px);
  padding: 24px 24px 26px;
}
.pay-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  font-size: 13.5px;
  color: var(--c-text-sub);
}
.pay-row.total {
  margin-top: 6px;
  padding-top: 16px;
  border-top: 1px solid var(--c-line);
  color: var(--c-text);
}
.wallet {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--c-text);
}
.wallet.low {
  color: var(--c-danger);
}
.recharge-link {
  font-size: 12.5px;
  color: var(--c-accent-deep);
  text-decoration: underline;
}
.short {
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--c-danger);
}
.submit {
  width: 100%;
  height: 46px;
  margin-top: 20px;
  letter-spacing: 0.16em;
}
.pay-tip {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--c-text-mute);
  text-align: center;
}

/* ---------- 成功 ---------- */
.done {
  max-width: 620px;
  margin: 20px auto 0;
  padding: 44px 40px;
  text-align: center;
}
.done-icon {
  width: 54px;
  height: 54px;
  margin: 0 auto 18px;
  line-height: 54px;
  font-size: 26px;
  color: #fff;
  background: var(--c-success);
  border-radius: 50%;
}
.done-title {
  font-family: var(--font-serif);
  font-size: 22px;
  letter-spacing: 0.08em;
}
.done-sub {
  margin-top: 10px;
  font-size: 13.5px;
  color: var(--c-text-sub);
}
.done-list {
  margin-top: 26px;
  border-top: 1px solid var(--c-line);
}
.done-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 2px;
  border-bottom: 1px solid var(--c-line);
  font-size: 13.5px;
}
.done-no {
  color: var(--c-text-sub);
}
.done-status {
  color: var(--c-accent-deep);
}
.done-actions {
  margin-top: 30px;
  display: flex;
  gap: 14px;
  justify-content: center;
  flex-wrap: wrap;
}

/* ---------- 充值弹层 ---------- */
.mask {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(51, 51, 51, 0.34);
  backdrop-filter: blur(2px);
}
.modal {
  width: 100%;
  max-width: 380px;
  padding: 28px 26px;
}
.modal-title {
  font-family: var(--font-serif);
  font-size: 19px;
  letter-spacing: 0.1em;
}
.modal-sub {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--c-text-mute);
}
.quick {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin: 20px 0 14px;
}
.quick button {
  padding: 9px 0;
  font-size: 13px;
  color: var(--c-text-sub);
  background: var(--c-bg);
  border: 1px solid var(--c-line);
  border-radius: var(--r-sm);
  transition: all 0.25s var(--ease);
}
.quick button:hover {
  color: var(--c-accent-deep);
  border-color: var(--c-accent);
}
.modal-input {
  width: 100%;
  padding: 11px 14px;
  font-size: 14px;
  background: var(--c-bg);
  border: 1px solid var(--c-line);
  border-radius: var(--r-sm);
}
.modal-input:focus {
  outline: none;
  border-color: var(--c-accent);
  box-shadow: 0 0 0 3px var(--c-accent-soft);
}
.modal-actions {
  margin-top: 22px;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

/* ---------- 提示 ---------- */
.toast {
  position: fixed;
  top: calc(var(--nav-h) + 20px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 400;
  padding: 11px 24px;
  font-size: 13.5px;
  border-radius: var(--r-pill);
  box-shadow: var(--sh-hover);
  background: #fff;
}
.toast-success {
  color: var(--c-success);
  border: 1px solid #d6e8de;
}
.toast-error {
  color: var(--c-danger);
  border: 1px solid #f2d7d3;
}
.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.3s var(--ease), transform 0.3s var(--ease);
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, -10px);
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s var(--ease);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .co-main {
    grid-template-columns: 1fr;
  }
  .pay {
    position: static;
  }
}
@media (max-width: 480px) {
  .title {
    font-size: 24px;
  }
  .block {
    padding: 20px;
  }
  .done {
    padding: 34px 22px;
  }
}
</style>
