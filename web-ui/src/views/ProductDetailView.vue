<script setup>
import { ref, computed, onMounted, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi, cartApi } from '@/api/modules'
import { chatApi } from '@/api/chat'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const product = ref(null)
const loading = ref(true)
const errorMsg = ref('')

/** 当前选中的主图 */
const activeImage = ref('')
/** 购买数量 */
const qty = ref(1)
/** 加购请求中（防止连点重复下单） */
const adding = ref(false)

/** 轻提示 */
const toast = ref({ show: false, type: 'success', text: '' })
let toastTimer = null

/** 后端 images / tags 是 JSON 字符串，容错解析成数组 */
function parseJsonArray(raw) {
  if (Array.isArray(raw)) return raw
  if (typeof raw !== 'string' || !raw.trim()) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

/** 画廊：优先多图，其次封面图，最后兜底 hero，避免空图 */
const gallery = computed(() => {
  const imgs = parseJsonArray(product.value?.images)
  if (imgs.length) return imgs
  if (product.value?.coverImage) return [product.value.coverImage]
  return ['/products/hero.png']
})

const tags = computed(() => parseJsonArray(product.value?.tags))
const price = computed(() => Number(product.value?.price ?? 0))
const stock = computed(() => Number(product.value?.stock ?? 0))
const soldOut = computed(() => stock.value <= 0)

async function loadProduct() {
  loading.value = true
  errorMsg.value = ''
  product.value = null
  try {
    const res = await productApi.detail(route.params.id)
    product.value = res.data
    qty.value = 1
    activeImage.value = gallery.value[0]
  } catch (e) {
    errorMsg.value = e.message || '作品加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function incQty() {
  if (qty.value < stock.value) qty.value += 1
}
function decQty() {
  if (qty.value > 1) qty.value -= 1
}
function onQtyInput(e) {
  const n = Math.floor(Number(e.target.value))
  if (Number.isNaN(n) || n < 1) qty.value = 1
  else qty.value = Math.min(n, Math.max(stock.value, 1))
}

function showToast(type, text) {
  toast.value = { show: true, type, text }
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value.show = false), 2400)
}

async function addToCart() {
  if (!userStore.isLogin) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (!userStore.userId) {
    showToast('error', '登录状态异常，请重新登录')
    return
  }
  if (soldOut.value) return

  adding.value = true
  try {
    await cartApi.add({
      userId: userStore.userId,
      productId: product.value.id,
      quantity: qty.value
    })
    showToast('success', '已加入购物车')
  } catch (e) {
    showToast('error', e.message || '加入购物车失败')
  } finally {
    adding.value = false
  }
}

let kefuCache = null

async function resolveKefu() {
  if (kefuCache) return kefuCache
  const res = await chatApi.kefu()
  const d = res.data || {}
  if (!d.userId) throw new Error('客服信息缺失')
  kefuCache = { id: Number(d.userId), nickname: d.nickname || '官方客服' }
  return kefuCache
}

/** 联系客服：带着商品上下文进入会话（没有会话就自动建一个） */
async function goChat() {
  if (!userStore.isLogin) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    const kefu = await resolveKefu()
    if (kefu.id === Number(userStore.userId)) {
      showToast('success', '你就是客服本人，去「消息」里查看用户留言吧')
      return
    }
    router.push({
      name: 'chat',
      query: { peer: kefu.id, productId: product.value.id, productName: product.value.name }
    })
  } catch (e) {
    showToast('error', e.message || '客服暂不可用，请稍后再试')
  }
}

onMounted(loadProduct)
watch(() => route.params.id, loadProduct)
onUnmounted(() => {
  if (toastTimer) clearTimeout(toastTimer)
})
</script>

<template>
  <div class="pd container">
    <!-- 面包屑 -->
    <nav class="crumb">
      <router-link to="/products">市集</router-link>
      <span class="crumb-sep">/</span>
      <span class="crumb-cur">{{ product?.name || '作品详情' }}</span>
    </nav>

    <!-- 加载中 -->
    <div v-if="loading" class="state">
      <div class="spinner"></div>
      <p>正在打开这件作品…</p>
    </div>

    <!-- 加载失败 / 不存在 -->
    <div v-else-if="errorMsg" class="state state-error">
      <p class="state-title">没能找到这件作品</p>
      <p class="state-desc">{{ errorMsg }}</p>
      <div class="state-actions">
        <button class="btn btn-primary" @click="loadProduct">重试</button>
        <router-link class="btn btn-ghost" to="/products">返回市集</router-link>
      </div>
    </div>

    <!-- 详情主体 -->
    <template v-else-if="product">
      <div class="pd-main">
        <!-- 左：画廊 -->
        <div class="gallery">
          <div class="gallery-main card">
            <img
              :src="activeImage"
              :alt="product.name"
              @error="(e) => (e.target.style.opacity = 0.15)"
            />
          </div>
          <div v-if="gallery.length > 1" class="thumbs">
            <button
              v-for="(img, i) in gallery"
              :key="i"
              :class="['thumb', { active: activeImage === img }]"
              @click="activeImage = img"
            >
              <img :src="img" :alt="`${product.name} 图${i + 1}`" />
            </button>
          </div>
        </div>

        <!-- 右：信息 -->
        <div class="info">
          <p v-if="product.category" class="info-cat">{{ product.category }}</p>
          <h1 class="info-name">{{ product.name }}</h1>

          <p v-if="product.story" class="info-story">「{{ product.story }}」</p>

          <div class="price-row">
            <span class="price price-lg">{{ price.toFixed(2) }}</span>
            <span v-if="soldOut" class="stock stock-out">已售罄</span>
            <span v-else class="stock">余 {{ stock }} 件</span>
          </div>

          <div v-if="tags.length" class="tags">
            <span v-for="t in tags" :key="t" class="tag">{{ t }}</span>
          </div>

          <ul class="meta">
            <li><span class="meta-k">浏览</span><span>{{ product.viewCount ?? 0 }}</span></li>
            <li><span class="meta-k">收藏</span><span>{{ product.likeCount ?? 0 }}</span></li>
          </ul>

          <!-- 数量 + 加购 -->
          <div class="buy">
            <div class="qty" :class="{ disabled: soldOut }">
              <button type="button" :disabled="soldOut || qty <= 1" @click="decQty">−</button>
              <input
                :value="qty"
                type="text"
                inputmode="numeric"
                :disabled="soldOut"
                @change="onQtyInput"
              />
              <button type="button" :disabled="soldOut || qty >= stock" @click="incQty">+</button>
            </div>

            <button
              class="btn btn-primary add-btn"
              :disabled="adding || soldOut"
              @click="addToCart"
            >
              {{ soldOut ? '已售罄' : adding ? '加入中…' : '加入购物车' }}
            </button>

            <button class="btn btn-ghost chat-btn" type="button" @click="goChat">
              联系客服
            </button>
          </div>

          <router-link class="link-cart" to="/cart">查看购物车 →</router-link>
        </div>
      </div>

      <!-- 创作故事 -->
      <section v-if="product.story" class="block story">
        <h2 class="block-title">创作故事</h2>
        <p class="story-text">{{ product.story }}</p>
      </section>

      <!-- 作品介绍 -->
      <section v-if="product.description" class="block">
        <h2 class="block-title">作品介绍</h2>
        <p class="block-text">{{ product.description }}</p>
      </section>
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
.pd {
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
  margin-bottom: 26px;
}
.crumb a:hover {
  color: var(--c-accent);
}
.crumb-sep {
  color: var(--c-line);
}
.crumb-cur {
  color: var(--c-text-sub);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 状态占位 ---------- */
.state {
  padding: 90px 0;
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

/* ---------- 主体两栏 ---------- */
.pd-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 48px;
  align-items: start;
}

/* ---------- 画廊 ---------- */
.gallery-main {
  aspect-ratio: 1 / 1;
  overflow: hidden;
}
.gallery-main img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.7s var(--ease);
}
.gallery-main:hover img {
  transform: scale(1.03);
}
.thumbs {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}
.thumb {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: var(--r-sm);
  border: 1.5px solid transparent;
  opacity: 0.7;
  transition: all 0.25s var(--ease);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb:hover {
  opacity: 1;
}
.thumb.active {
  opacity: 1;
  border-color: var(--c-accent);
}

/* ---------- 信息栏 ---------- */
.info {
  padding-top: 6px;
}
.info-cat {
  display: inline-block;
  padding: 3px 12px;
  font-size: 12px;
  letter-spacing: 0.1em;
  color: var(--c-accent-deep);
  background: var(--c-accent-soft);
  border-radius: var(--r-pill);
}
.info-name {
  margin-top: 16px;
  font-family: var(--font-serif);
  font-size: 30px;
  font-weight: 500;
  line-height: 1.45;
  letter-spacing: 0.05em;
}
.info-story {
  margin-top: 14px;
  font-size: 14px;
  line-height: 2;
  color: var(--c-text-sub);
}

.price-row {
  margin-top: 24px;
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--c-line);
}
.price-lg {
  font-size: 28px;
}
.stock {
  font-size: 13.5px;
  color: var(--c-text-mute);
}
.stock-out {
  color: var(--c-danger);
}

.tags {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta {
  margin-top: 22px;
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  font-size: 13px;
  color: var(--c-text-sub);
}
.meta li {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.meta-k {
  color: var(--c-text-mute);
}

/* ---------- 购买区 ---------- */
.buy {
  margin-top: 30px;
  display: flex;
  gap: 14px;
  align-items: stretch;
}
.qty {
  display: flex;
  align-items: center;
  height: 46px;
  border: 1px solid var(--c-line);
  border-radius: var(--r-pill);
  overflow: hidden;
  background: var(--c-surface);
}
.qty.disabled {
  opacity: 0.5;
}
.qty button {
  width: 42px;
  height: 100%;
  font-size: 18px;
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
  width: 46px;
  height: 100%;
  text-align: center;
  font-size: 15px;
  border: none;
  background: transparent;
}
.add-btn {
  flex: 1;
  height: 46px;
  letter-spacing: 0.14em;
}
.chat-btn {
  flex: 0 0 auto;
  height: 46px;
  padding: 0 22px;
  letter-spacing: 0.08em;
}

.link-cart {
  display: inline-block;
  margin-top: 16px;
  font-size: 13.5px;
  color: var(--c-accent-deep);
}
.link-cart:hover {
  color: var(--c-accent);
  text-decoration: underline;
}

/* ---------- 下方区块 ---------- */
.block {
  margin-top: 60px;
  padding-top: 34px;
  border-top: 1px solid var(--c-line);
}
.block-title {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 500;
  letter-spacing: 0.14em;
}
.story-text {
  margin-top: 18px;
  padding-left: 20px;
  border-left: 3px solid var(--c-accent);
  font-family: var(--font-serif);
  font-size: 16px;
  line-height: 2.1;
  color: var(--c-text);
}
.block-text {
  margin-top: 16px;
  font-size: 14.5px;
  line-height: 2.1;
  color: var(--c-text-sub);
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
  .pd-main {
    grid-template-columns: 1fr;
    gap: 30px;
  }
  .info-name {
    font-size: 25px;
  }
  .thumbs {
    grid-template-columns: repeat(4, 1fr);
  }
}
@media (max-width: 480px) {
  .buy {
    flex-direction: column;
  }
  .qty {
    justify-content: space-between;
  }
}
</style>
