<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { cartApi } from '@/api/modules'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

/** 单项数量上限（商品库存未随购物车下发，先给一个安全默认值） */
const MAX_QTY = 99
/** 商品图缺失时的兜底 */
const FALLBACK_IMG = '/products/hero.png'

const items = ref([])
const loading = ref(true)
const errorMsg = ref('')
/** 已勾选的商品ID集合（前端本地维护，用于结算） */
const selected = ref(new Set())
/** 批量操作（清空等）进行中 */
const busy = ref(false)
/** 单项请求中的商品ID，避免同一行连点 */
const pending = ref({})

const toast = ref({ show: false, type: 'success', text: '' })
let toastTimer = null

const validItems = computed(() => items.value.filter((i) => !i.invalid))
const selectedItems = computed(() =>
  items.value.filter((i) => !i.invalid && selected.value.has(i.productId))
)
const allSelected = computed(
  () => validItems.value.length > 0 && validItems.value.every((i) => selected.value.has(i.productId))
)
/** 已选商品件数（按数量累加，符合电商购物车习惯） */
const selectedCount = computed(() =>
  selectedItems.value.reduce((sum, i) => sum + Number(i.quantity || 0), 0)
)
/** 已选合计金额 */
const totalAmount = computed(() =>
  selectedItems.value.reduce((sum, i) => sum + Number(i.unitPrice || 0) * Number(i.quantity || 0), 0)
)

function subtotal(item) {
  return Number(item.unitPrice || 0) * Number(item.quantity || 0)
}
function isPending(productId) {
  return !!pending.value[productId]
}
function setPending(productId, flag) {
  const next = { ...pending.value }
  if (flag) next[productId] = true
  else delete next[productId]
  pending.value = next
}

function showToast(type, text) {
  toast.value = { show: true, type, text }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value.show = false), 2400)
}

async function loadCart() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await cartApi.list(userStore.userId)
    items.value = Array.isArray(res.data) ? res.data : []
    // 默认全部勾选有效商品（常见电商交互：进来就能直接结算）
    selected.value = new Set(validItems.value.map((i) => i.productId))
  } catch (e) {
    errorMsg.value = e.message || '购物车加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function toggleSelect(item) {
  if (item.invalid) return
  const next = new Set(selected.value)
  if (next.has(item.productId)) next.delete(item.productId)
  else next.add(item.productId)
  selected.value = next
}

function toggleAll() {
  selected.value = allSelected.value
    ? new Set()
    : new Set(validItems.value.map((i) => i.productId))
}

function clampQty(n) {
  if (Number.isNaN(n) || n < 1) return 1
  return Math.min(n, MAX_QTY)
}

/** 提交数量变更：乐观更新 + 失败回滚，保证 UI 与后端一致 */
async function commitQty(item, next) {
  if (item.invalid || busy.value || isPending(item.productId)) return
  const prev = Number(item.quantity)
  if (next === prev) return

  item.quantity = next
  setPending(item.productId, true)
  try {
    await cartApi.updateQuantity({
      userId: userStore.userId,
      productId: item.productId,
      quantity: next
    })
  } catch (e) {
    item.quantity = prev
    showToast('error', e.message || '修改数量失败')
  } finally {
    setPending(item.productId, false)
  }
}

function changeQty(item, delta) {
  commitQty(item, clampQty(Number(item.quantity) + delta))
}

function onQtyInput(item, e) {
  const n = Math.floor(Number(e.target.value))
  commitQty(item, clampQty(n))
}

async function removeItem(item) {
  if (busy.value || isPending(item.productId)) return
  setPending(item.productId, true)
  try {
    await cartApi.remove({ userId: userStore.userId, productId: item.productId })
    items.value = items.value.filter((i) => i.productId !== item.productId)
    const next = new Set(selected.value)
    next.delete(item.productId)
    selected.value = next
    showToast('success', '已移出购物车')
  } catch (e) {
    showToast('error', e.message || '删除失败')
  } finally {
    setPending(item.productId, false)
  }
}

async function clearCart() {
  if (busy.value || !items.value.length) return
  if (!window.confirm('确定要清空购物车吗？')) return
  busy.value = true
  try {
    await cartApi.clear(userStore.userId)
    items.value = []
    selected.value = new Set()
    showToast('success', '购物车已清空')
  } catch (e) {
    showToast('error', e.message || '清空失败')
  } finally {
    busy.value = false
  }
}

function goCheckout() {
  if (!selectedItems.value.length) {
    showToast('error', '请先选择要结算的作品')
    return
  }
  // 只带走勾选项的「购物车记录ID」，交给「确认订单」阶段使用
  // 注意：结算接口 /cart/checkout 要的是 cartId，不是 productId
  sessionStorage.setItem(
    'checkout_cart_ids',
    JSON.stringify(selectedItems.value.map((i) => i.cartId))
  )
  router.push({ name: 'checkout' })
}

onMounted(loadCart)
onUnmounted(() => {
  if (toastTimer) clearTimeout(toastTimer)
})
</script>

<template>
  <div class="cart container">
    <header class="cart-head">
      <div>
        <h1 class="title">购物车</h1>
        <p class="sub">挑好了，就带它们回家</p>
      </div>
      <button v-if="items.length" class="btn-plain clear" :disabled="busy" @click="clearCart">
        清空购物车
      </button>
    </header>

    <!-- 加载中 -->
    <div v-if="loading" class="state">
      <div class="spinner"></div>
      <p>正在翻开你的购物车…</p>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="errorMsg" class="state state-error">
      <p class="state-title">购物车没能打开</p>
      <p class="state-desc">{{ errorMsg }}</p>
      <div class="state-actions">
        <button class="btn btn-primary" @click="loadCart">重试</button>
        <router-link class="btn btn-ghost" to="/products">去市集逛逛</router-link>
      </div>
    </div>

    <!-- 空车 -->
    <div v-else-if="!items.length" class="state">
      <p class="state-title">购物车还是空的</p>
      <p class="state-desc">去市集看看手作者们的新作品吧</p>
      <div class="state-actions">
        <router-link class="btn btn-primary" to="/products">去市集</router-link>
      </div>
    </div>

    <!-- 列表 -->
    <template v-else>
      <ul class="list">
        <li v-for="item in items" :key="item.productId" :class="['row', { invalid: item.invalid }]">
          <label class="pick">
            <input
              type="checkbox"
              :checked="!item.invalid && selected.has(item.productId)"
              :disabled="item.invalid"
              @change="toggleSelect(item)"
            />
          </label>

          <router-link
            class="thumb"
            :to="item.invalid ? '/products' : `/product/${item.productId}`"
          >
            <img
              :src="item.coverImage || FALLBACK_IMG"
              :alt="item.productName"
              @error="(e) => (e.target.src = FALLBACK_IMG)"
            />
            <span v-if="item.invalid" class="badge">已失效</span>
          </router-link>

          <div class="meta">
            <router-link
              class="name"
              :to="item.invalid ? '/products' : `/product/${item.productId}`"
            >
              {{ item.productName || '未知作品' }}
            </router-link>
            <p class="unit">单价 <span class="price price-sm">{{ Number(item.unitPrice || 0).toFixed(2) }}</span></p>
          </div>

          <div class="qty" :class="{ disabled: item.invalid || isPending(item.productId) }">
            <button
              type="button"
              :disabled="item.invalid || isPending(item.productId) || Number(item.quantity) <= 1"
              @click="changeQty(item, -1)"
            >−</button>
            <input
              :value="item.quantity"
              type="text"
              inputmode="numeric"
              :disabled="item.invalid || isPending(item.productId)"
              @change="(e) => onQtyInput(item, e)"
            />
            <button
              type="button"
              :disabled="item.invalid || isPending(item.productId) || Number(item.quantity) >= MAX_QTY"
              @click="changeQty(item, 1)"
            >+</button>
          </div>

          <p class="subtotal">
            <span class="price">{{ subtotal(item).toFixed(2) }}</span>
          </p>

          <button
            class="remove"
            :disabled="busy || isPending(item.productId)"
            title="移出购物车"
            @click="removeItem(item)"
          >
            移出
          </button>
        </li>
      </ul>

      <!-- 结算栏 -->
      <div class="bar">
        <label class="pick-all">
          <input type="checkbox" :checked="allSelected" :disabled="!validItems.length" @change="toggleAll" />
          <span>全选</span>
        </label>

        <div class="bar-right">
          <p class="bar-info">
            已选 <em>{{ selectedItems.length }}</em> 种 / <em>{{ selectedCount }}</em> 件
          </p>
          <p class="bar-total">
            合计 <span class="price price-lg">{{ totalAmount.toFixed(2) }}</span>
          </p>
          <button
            class="btn btn-primary checkout-btn"
            :disabled="!selectedItems.length || busy"
            @click="goCheckout"
          >
            去结算
          </button>
        </div>
      </div>
    </template>

    <!-- 结果提示 -->
    <transition name="toast">
      <div v-if="toast.show" :class="['toast', `toast-${toast.type}`]">
        {{ toast.text }}
      </div>
    </transition>
  </div>
</template>

<style scoped>
.cart {
  padding-top: 52px;
  padding-bottom: 90px;
}

.cart-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 28px;
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
.clear {
  font-size: 13.5px;
  color: var(--c-text-sub);
  transition: color 0.25s var(--ease);
}
.clear:hover:not(:disabled) {
  color: var(--c-danger);
}
.clear:disabled {
  cursor: not-allowed;
  opacity: 0.5;
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

/* ---------- 列表 ---------- */
.list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.row {
  display: grid;
  grid-template-columns: 28px 96px minmax(0, 1fr) auto 110px 64px;
  align-items: center;
  gap: 18px;
  padding: 16px 20px;
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--r-md);
  box-shadow: var(--sh-card);
  transition: box-shadow 0.3s var(--ease), transform 0.3s var(--ease);
}
.row:hover {
  box-shadow: var(--sh-hover);
}
.row.invalid {
  opacity: 0.62;
}

.pick input,
.pick-all input {
  width: 17px;
  height: 17px;
  cursor: pointer;
  accent-color: var(--c-accent);
}
.pick input:disabled {
  cursor: not-allowed;
}

.thumb {
  position: relative;
  display: block;
  width: 96px;
  height: 96px;
  border-radius: var(--r-sm);
  overflow: hidden;
  background: var(--c-bg);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s var(--ease);
}
.row:not(.invalid) .thumb:hover img {
  transform: scale(1.05);
}
.row.invalid .thumb img {
  filter: grayscale(1);
}
.badge {
  position: absolute;
  inset: auto 0 0 0;
  padding: 4px 0;
  text-align: center;
  font-size: 11.5px;
  letter-spacing: 0.08em;
  color: #fff;
  background: rgba(60, 60, 60, 0.72);
}

.meta {
  min-width: 0;
}
.name {
  display: block;
  font-family: var(--font-serif);
  font-size: 16.5px;
  letter-spacing: 0.04em;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.name:hover {
  color: var(--c-accent-deep);
}
.unit {
  margin-top: 10px;
  font-size: 13px;
  color: var(--c-text-mute);
}

.price {
  color: var(--c-accent-deep);
  font-weight: 600;
}
.price-sm {
  font-size: 13.5px;
}
.price-lg {
  font-size: 22px;
}

/* ---------- 数量 ---------- */
.qty {
  display: flex;
  align-items: center;
  height: 36px;
  border: 1px solid var(--c-line);
  border-radius: var(--r-pill);
  overflow: hidden;
  background: #fff;
}
.qty.disabled {
  opacity: 0.5;
}
.qty button {
  width: 34px;
  height: 100%;
  font-size: 16px;
  color: var(--c-text-sub);
  transition: color 0.2s var(--ease), background 0.2s var(--ease);
}
.qty button:hover:not(:disabled) {
  color: var(--c-accent-deep);
  background: var(--c-accent-soft);
}
.qty button:disabled {
  cursor: not-allowed;
}
.qty input {
  width: 40px;
  height: 100%;
  text-align: center;
  font-size: 14px;
  border: none;
  background: transparent;
}

.subtotal {
  text-align: right;
  font-size: 17px;
}

.remove {
  font-size: 13px;
  color: var(--c-text-mute);
  transition: color 0.25s var(--ease);
}
.remove:hover:not(:disabled) {
  color: var(--c-danger);
}
.remove:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

/* ---------- 结算栏 ---------- */
.bar {
  position: sticky;
  bottom: 20px;
  margin-top: 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: saturate(180%) blur(12px);
  border: 1px solid var(--c-line);
  border-radius: var(--r-md);
  box-shadow: var(--sh-hover);
}
.pick-all {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 14px;
  color: var(--c-text-sub);
  cursor: pointer;
}
.bar-right {
  display: flex;
  align-items: center;
  gap: 24px;
}
.bar-info {
  font-size: 13.5px;
  color: var(--c-text-sub);
}
.bar-info em {
  font-style: normal;
  font-weight: 600;
  color: var(--c-accent-deep);
}
.bar-total {
  font-size: 13.5px;
  color: var(--c-text-sub);
}
.checkout-btn {
  height: 46px;
  padding: 0 40px;
  letter-spacing: 0.16em;
}

/* ---------- 提示 ---------- */
.toast {
  position: fixed;
  top: calc(var(--nav-h) + 20px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 200;
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

/* ---------- 响应式 ---------- */
@media (max-width: 860px) {
  .row {
    grid-template-columns: 24px 76px minmax(0, 1fr) auto;
    grid-template-areas:
      'pick thumb meta subtotal'
      '.    qty   remove  .';
    row-gap: 12px;
    padding: 14px;
  }
  .pick { grid-area: pick; }
  .thumb { grid-area: thumb; width: 76px; height: 76px; }
  .meta { grid-area: meta; }
  .qty { grid-area: qty; justify-self: start; }
  .subtotal { grid-area: subtotal; }
  .remove { grid-area: remove; justify-self: end; }

  .bar {
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
  }
  .bar-right {
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 14px;
  }
  .checkout-btn {
    flex: 1;
  }
}
@media (max-width: 480px) {
  .cart-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
  .title {
    font-size: 25px;
  }
}
</style>
