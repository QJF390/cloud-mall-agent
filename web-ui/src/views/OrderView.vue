<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { orderApi } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import LogisticsTrack from '@/components/LogisticsTrack.vue'

const userStore = useUserStore()

/** 状态筛选：all 或具体状态码 */
const statusFilter = ref('all')

const loading = ref(true)
const errorMsg = ref('')
/** 我买到的订单（B2C 自营：用户只有买家身份） */
const orders = ref([])
/** orderId -> OrderItem[]（商品快照，来自 /order/items） */
const itemsMap = ref({})

/** 正在执行动作的订单ID，用于禁用按钮防连点 */
const actingId = ref(null)

const STATUS_META = {
  PENDING: { text: '待付款', cls: 'st-pending' },
  FROZEN: { text: '已付款 · 待发货', cls: 'st-frozen' },
  SHIPPED: { text: '已发货', cls: 'st-shipped' },
  RECEIVED: { text: '已收货', cls: 'st-done' },
  COMPLETED: { text: '已完成', cls: 'st-done' },
  CANCELLED: { text: '已取消', cls: 'st-cancelled' },
  REFUNDED: { text: '已退款', cls: 'st-refunded' }
}

const FILTERS = [
  { value: 'all', label: '全部' },
  { value: 'FROZEN', label: '待发货' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' }
]

/** 过滤后的列表 */
const list = computed(() =>
  statusFilter.value === 'all'
    ? orders.value
    : orders.value.filter((o) => o.status === statusFilter.value)
)

/** 各状态数量（用于筛选点缀，也方便你核对后端真实状态分布） */
const counts = computed(() => {
  const c = {}
  orders.value.forEach((o) => (c[o.status] = (c[o.status] || 0) + 1))
  return c
})

function statusMeta(s) {
  return STATUS_META[s] || { text: s || '未知', cls: 'st-pending' }
}

function formatTime(t) {
  if (!t) return ''
  // 兼容后端可能返回 ISO 字符串或数组形式 [y,m,d,h,mi,s]
  if (Array.isArray(t)) {
    const [y, m, d, h = 0, mi = 0] = t
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}`
  }
  const s = String(t).replace('T', ' ')
  return s.slice(0, 16)
}

function itemsOf(orderId) {
  return itemsMap.value[String(orderId)] || []
}

function orderActions(o) {
  return {
    cancel: o.status === 'PENDING' || o.status === 'FROZEN',
    receive: o.status === 'SHIPPED',
    refund: o.status === 'SHIPPED',
    // 只有进入运输环节（已发货及之后）才有物流可看；取消/退款单没有轨迹
    track: ['SHIPPED', 'RECEIVED', 'COMPLETED'].includes(o.status)
  }
}

/** 正在查看物流的订单；null 表示弹层关闭 */
const trackOrder = ref(null)

function openTrack(o) {
  trackOrder.value = o
  // 物流弹层内容较高，锁住背景滚动，避免滚动穿透
  document.body.style.overflow = 'hidden'
}

function closeTrack() {
  trackOrder.value = null
  document.body.style.overflow = ''
}

const toast = ref({ show: false, type: 'success', text: '' })
let toastTimer = null
function showToast(type, text) {
  toast.value = { show: true, type, text }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value.show = false), 2600)
}

/** 通用确认弹层（退款时额外要求填写原因） */
const dialog = ref({
  open: false,
  title: '',
  desc: '',
  confirmText: '确定',
  danger: false,
  needReason: false
})
const dialogReason = ref('')
let dialogAction = null

function openDialog(opts, action) {
  dialog.value = {
    open: true,
    confirmText: '确定',
    danger: false,
    needReason: false,
    title: '',
    desc: '',
    ...opts
  }
  dialogReason.value = ''
  dialogAction = action
}

function closeDialog() {
  dialog.value.open = false
  dialogAction = null
}

/** 批量拉取商品快照 */
async function loadItems(orderIds) {
  if (!orderIds.length) {
    itemsMap.value = {}
    return
  }
  const res = await orderApi.items(orderIds)
  const grouped = {}
  ;(res.data || []).forEach((it) => {
    const key = String(it.orderId)
    if (!grouped[key]) grouped[key] = []
    grouped[key].push(it)
  })
  itemsMap.value = grouped
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await orderApi.listBuy(userStore.userId)
    orders.value = Array.isArray(res.data) ? res.data : []
    // 一次批量取回全部订单明细，避免逐单 N+1
    await loadItems(orders.value.map((o) => o.id))
  } catch (e) {
    errorMsg.value = e.message || '订单加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 执行订单动作：统一防连点 + 成功后刷新真实数据 */
async function runAction(order, fn) {
  if (actingId.value) return
  actingId.value = order.id
  try {
    await fn()
    showToast('success', '操作成功')
    closeDialog()
    await load()
  } catch (e) {
    showToast('error', e.message || '操作失败，请稍后重试')
  } finally {
    actingId.value = null
  }
}

function onCancel(o) {
  openDialog(
    {
      title: '取消订单',
      desc: `将取消订单 ${o.orderNo}，平台会解冻资金并回补库存。`,
      confirmText: '确认取消',
      danger: true
    },
    () => runAction(o, () => orderApi.cancel({ userId: userStore.userId, orderId: o.id }))
  )
}

function onReceive(o) {
  openDialog(
    {
      title: '确认收货',
      desc: `确认已收到订单 ${o.orderNo} 的作品后，平台将完成本次交易结算。`,
      confirmText: '确认收货'
    },
    () => runAction(o, () => orderApi.receive({ buyerId: userStore.userId, orderId: o.id }))
  )
}

function onRefund(o) {
  openDialog(
    {
      title: '申请退款',
      desc: `订单 ${o.orderNo} 的资金仍在平台冻结，退款后原路返回余额。`,
      confirmText: '提交退款',
      danger: true,
      needReason: true
    },
    () =>
      runAction(o, () =>
        orderApi.refund({
          userId: userStore.userId,
          orderId: o.id,
          reason: dialogReason.value.trim()
        })
      )
  )
}

async function confirmDialog() {
  if (dialog.value.needReason && !dialogReason.value.trim()) {
    showToast('error', '请填写退款原因')
    return
  }
  if (dialogAction) await dialogAction()
}

onMounted(load)
onUnmounted(() => {
  if (toastTimer) clearTimeout(toastTimer)
  // 兜底恢复滚动：否则用户在弹层打开时切走路由，整页会被永久锁住滚不动
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="orders container">
    <h1 class="title">我的订单</h1>

    <!-- 状态筛选 -->
    <div class="filters">
      <button
        v-for="f in FILTERS"
        :key="f.value"
        :class="['chip', { active: statusFilter === f.value }]"
        @click="statusFilter = f.value"
      >
        {{ f.label }}
        <span v-if="f.value !== 'all' && counts[f.value]" class="chip-num">{{ counts[f.value] }}</span>
      </button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="state">
      <div class="spinner"></div>
      <p>正在加载订单…</p>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="errorMsg" class="state">
      <p class="state-title">订单没能加载出来</p>
      <p class="state-desc">{{ errorMsg }}</p>
      <div class="state-actions">
        <button class="btn btn-primary" @click="load">重试</button>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else-if="!list.length" class="state">
      <p class="state-title">
        {{ statusFilter === 'all' ? '还没有买到的作品' : '该状态下暂无订单' }}
      </p>
      <p class="state-desc">去市集逛逛，遇见喜欢的作品</p>
      <div class="state-actions">
        <router-link class="btn btn-primary" to="/products">去市集</router-link>
      </div>
    </div>

    <!-- 订单列表 -->
    <ul v-else class="list">
      <li v-for="o in list" :key="o.id" class="order card">
        <!-- 单头：订单号 / 时间 / 状态 -->
        <div class="order-head">
          <div class="head-left">
            <span class="order-no">订单号 {{ o.orderNo }}</span>
            <span class="order-time">{{ formatTime(o.createTime) }}</span>
          </div>
          <span :class="['status', statusMeta(o.status).cls]">{{ statusMeta(o.status).text }}</span>
        </div>

        <!-- 商品快照（真实来自 t_order_item） -->
        <ul class="goods">
          <li v-for="it in itemsOf(o.id)" :key="it.id" class="good">
            <div class="good-meta">
              <p class="good-name">{{ it.productName || '未知作品' }}</p>
              <p class="good-unit">
                <span class="price price-sm">{{ Number(it.unitPrice || 0).toFixed(2) }}</span>
                × {{ it.quantity }}
              </p>
            </div>
            <span class="good-sub price">{{ Number(it.totalAmount || 0).toFixed(2) }}</span>
          </li>
          <li v-if="!itemsOf(o.id).length" class="good good-empty">明细加载中…</li>
        </ul>

        <!-- 收货 / 交易信息 -->
        <div class="info">
          <p class="info-row">
            <span class="info-k">店铺</span>
            <span class="info-v">平台自营</span>
          </p>
          <p class="info-row">
            <span class="info-k">收货人</span>
            <span class="info-v">
              {{ o.receiverName || '—' }} {{ o.receiverPhone ? '· ' + o.receiverPhone : '' }}
            </span>
          </p>
          <p class="info-row">
            <span class="info-k">地址</span>
            <span class="info-v">{{ o.receiverAddress || '—' }}</span>
          </p>
          <p v-if="o.refundReason" class="info-row">
            <span class="info-k">退款原因</span>
            <span class="info-v">{{ o.refundReason }}</span>
          </p>
        </div>

        <!-- 单脚：金额 + 操作 -->
        <div class="order-foot">
          <p class="amount">
            共 {{ itemsOf(o.id).reduce((s, i) => s + Number(i.quantity || 0), 0) }} 件 · 实付
            <span class="price price-lg">{{ Number(o.totalAmount || 0).toFixed(2) }}</span>
          </p>

          <div class="actions">
            <button
              v-if="orderActions(o).track"
              class="btn btn-ghost btn-sm"
              @click="openTrack(o)"
            >
              物流跟踪
            </button>
            <button
              v-if="orderActions(o).cancel"
              class="btn btn-ghost btn-sm"
              :disabled="actingId === o.id"
              @click="onCancel(o)"
            >
              取消订单
            </button>
            <button
              v-if="orderActions(o).refund"
              class="btn btn-ghost btn-sm"
              :disabled="actingId === o.id"
              @click="onRefund(o)"
            >
              申请退款
            </button>
            <button
              v-if="orderActions(o).receive"
              class="btn btn-primary btn-sm"
              :disabled="actingId === o.id"
              @click="onReceive(o)"
            >
              确认收货
            </button>
          </div>
        </div>
      </li>
    </ul>

    <!-- 确认弹层 -->
    <transition name="fade">
      <div v-if="dialog.open" class="mask" @click.self="closeDialog">
        <div class="modal card">
          <h3 class="modal-title">{{ dialog.title }}</h3>
          <p class="modal-sub">{{ dialog.desc }}</p>

          <textarea
            v-if="dialog.needReason"
            v-model="dialogReason"
            class="modal-input"
            rows="3"
            placeholder="请填写退款原因（如：商品与描述不符）"
          ></textarea>

          <div class="modal-actions">
            <button class="btn btn-ghost" @click="closeDialog">再想想</button>
            <button
              :class="['btn', dialog.danger ? 'btn-danger' : 'btn-primary']"
              :disabled="!!actingId"
              @click="confirmDialog"
            >
              {{ actingId ? '处理中…' : dialog.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 物流跟踪弹层 -->
    <transition name="fade">
      <div v-if="trackOrder" class="mask" @click.self="closeTrack">
        <div class="modal modal-lg card">
          <LogisticsTrack :order="trackOrder" @close="closeTrack" />
        </div>
      </div>
    </transition>

    <!-- 结果提示 -->
    <transition name="toast">
      <div v-if="toast.show" :class="['toast', `toast-${toast.type}`]">{{ toast.text }}</div>
    </transition>
  </div>
</template>

<style scoped>
.orders {
  padding-top: 40px;
  padding-bottom: 90px;
}

.title {
  font-family: var(--font-serif);
  font-size: 30px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

/* ---------- Tab ---------- */
.tabs {
  display: flex;
  gap: 28px;
  margin: 26px 0 14px;
  border-bottom: 1px solid var(--c-line);
}
.tab {
  position: relative;
  padding: 10px 2px 14px;
  font-size: 16px;
  color: var(--c-text-sub);
  transition: color 0.25s var(--ease);
}
.tab:hover {
  color: var(--c-text);
}
.tab.active {
  color: var(--c-text);
  font-weight: 600;
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
.tab-num {
  display: inline-block;
  min-width: 18px;
  margin-left: 4px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  color: var(--c-text-sub);
  background: var(--c-bg-deep);
  border-radius: var(--r-pill);
}

/* ---------- 状态筛选 ---------- */
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 22px;
}
.chip {
  padding: 6px 14px;
  font-size: 13.5px;
  color: var(--c-text-sub);
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--r-pill);
  transition: all 0.25s var(--ease);
}
.chip:hover {
  color: var(--c-accent-deep);
  border-color: var(--c-accent);
}
.chip.active {
  color: #fff;
  background: var(--c-accent);
  border-color: var(--c-accent);
}
.chip-num {
  margin-left: 4px;
  opacity: 0.75;
}

/* ---------- 通用状态块 ---------- */
.state {
  padding: 90px 0;
  text-align: center;
  color: var(--c-text-sub);
}
.state-title {
  font-size: 17px;
  color: var(--c-text);
}
.state-desc {
  margin-top: 8px;
  font-size: 13.5px;
  color: var(--c-text-mute);
}
.state-actions {
  margin-top: 22px;
  display: flex;
  gap: 12px;
  justify-content: center;
}
.spinner {
  width: 30px;
  height: 30px;
  margin: 0 auto 14px;
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

/* ---------- 订单卡 ---------- */
.list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.order {
  padding: 20px 22px;
  cursor: default;
}
.order:hover {
  transform: none;
  box-shadow: var(--sh-card);
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--c-line);
}
.head-left {
  display: flex;
  align-items: baseline;
  gap: 14px;
  flex-wrap: wrap;
}
.order-no {
  font-size: 14px;
  font-weight: 600;
}
.order-time {
  font-size: 12.5px;
  color: var(--c-text-mute);
}

.status {
  flex-shrink: 0;
  padding: 3px 12px;
  font-size: 12.5px;
  border-radius: var(--r-pill);
}
.st-pending {
  color: var(--c-warn);
  background: rgba(217, 164, 65, 0.12);
}
.st-frozen {
  color: var(--c-accent-deep);
  background: var(--c-accent-soft);
}
.st-shipped {
  color: #4a7fb5;
  background: rgba(74, 127, 181, 0.12);
}
.st-done {
  color: var(--c-success);
  background: rgba(91, 154, 122, 0.12);
}
.st-cancelled {
  color: var(--c-text-mute);
  background: var(--c-bg-deep);
}
.st-refunded {
  color: var(--c-danger);
  background: rgba(217, 115, 106, 0.12);
}

/* ---------- 商品快照 ---------- */
.goods {
  padding: 14px 0;
}
.good {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
}
.good + .good {
  border-top: 1px solid var(--c-line);
}
.good-name {
  font-size: 14.5px;
  color: var(--c-text);
}
.good-unit {
  margin-top: 2px;
  font-size: 12.5px;
  color: var(--c-text-mute);
}
.good-sub {
  flex-shrink: 0;
  font-size: 14px;
}
.good-empty {
  font-size: 13px;
  color: var(--c-text-mute);
}
.price-sm {
  font-size: 13px;
}

/* ---------- 交易 / 收货信息 ---------- */
.info {
  padding: 14px 16px;
  background: var(--c-bg-deep);
  border-radius: var(--r-md);
}
.info-row {
  display: flex;
  gap: 12px;
  font-size: 13px;
  line-height: 1.9;
}
.info-k {
  flex-shrink: 0;
  width: 56px;
  color: var(--c-text-mute);
}
.info-v {
  color: var(--c-text-sub);
  word-break: break-all;
}

/* ---------- 单脚 ---------- */
.order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.amount {
  font-size: 13.5px;
  color: var(--c-text-sub);
}
.price-lg {
  font-size: 19px;
  margin-left: 4px;
}
.actions {
  display: flex;
  gap: 10px;
}
.btn-sm {
  height: 34px;
  padding: 0 18px;
  font-size: 13.5px;
}

.btn-danger {
  background: var(--c-danger);
  color: #fff;
}
.btn-danger:hover {
  background: #c9635a;
  transform: translateY(-2px);
}
.btn-danger:disabled {
  opacity: 0.6;
}

/* ---------- 弹层 ---------- */
.mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(51, 51, 51, 0.32);
  backdrop-filter: blur(2px);
}
.modal {
  width: min(440px, 90vw);
  padding: 26px 24px;
}
.modal:hover {
  transform: none;
  box-shadow: var(--sh-hover);
}
/* 物流弹层：比确认框宽得多，内边距由 LogisticsTrack 自己控制 */
.modal-lg {
  width: min(940px, 94vw);
  padding: 0;
  overflow: hidden;
}
.modal-title {
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 600;
}
.modal-sub {
  margin-top: 10px;
  font-size: 13.5px;
  color: var(--c-text-sub);
  line-height: 1.8;
}
.modal-input {
  width: 100%;
  margin-top: 16px;
  padding: 10px 12px;
  font-size: 14px;
  background: var(--c-bg-deep);
  border: 1px solid var(--c-line);
  border-radius: var(--r-md);
  resize: none;
}
.modal-input:focus {
  border-color: var(--c-accent);
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 22px;
}

/* ---------- Toast ---------- */
.toast {
  position: fixed;
  left: 50%;
  bottom: 40px;
  transform: translateX(-50%);
  z-index: 300;
  padding: 10px 22px;
  font-size: 14px;
  color: #fff;
  background: rgba(51, 51, 51, 0.92);
  border-radius: var(--r-pill);
}
.toast-success {
  background: rgba(91, 154, 122, 0.95);
}
.toast-error {
  background: rgba(217, 115, 106, 0.95);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.22s var(--ease);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.25s var(--ease), transform 0.25s var(--ease);
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 10px);
}

@media (max-width: 640px) {
  .order-foot {
    flex-direction: column;
    align-items: flex-start;
  }
  .actions {
    width: 100%;
  }
  .actions .btn {
    flex: 1;
  }
}
</style>
