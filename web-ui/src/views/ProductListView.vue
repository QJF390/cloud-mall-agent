<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { productApi } from '@/api/modules'
import { useUserStore } from '@/stores/user'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()
const userStore = useUserStore()

const FALLBACK_PRODUCTS = [
  { id: 'demo-1', name: '粗陶手作咖啡杯', category: '陶瓷', story: '泥土在指尖成型，窑火赋予它温度。', price: 128, stock: 10, coverImage: '/products/ceramic_coffee_cup.png' },
  { id: 'demo-2', name: '植物染亚麻帆布袋', category: '布艺', story: '板蓝根与栀子煮出的颜色，会慢慢褪色。', price: 168, stock: 8, coverImage: '/products/linen_tote_bag.png' },
  { id: 'demo-3', name: '手工银质细戒指', category: '饰品', story: '银会记住你佩戴的每一天。', price: 89, stock: 15, coverImage: '/products/silver_ring.png' },
  { id: 'demo-4', name: '黑胡桃木手机支架', category: '木作', story: '打磨出适合手掌的弧度。', price: 118, stock: 12, coverImage: '/products/walnut_phone_stand.png' }
]

const ALL = '全部'
const PAGE_SIZE = 60

const keyword = ref('')
const activeCategory = ref(ALL)
const categories = ref([ALL])
const products = ref([])
const loading = ref(true)
const isDemo = ref(false)

/** 分类导航走后端：只返回"真实在售"的分类，避免空分类点了进去什么都没 */
async function loadCategories() {
  try {
    const res = await productApi.categories()
    const list = Array.isArray(res.data) ? res.data : []
    categories.value = [ALL, ...list]
  } catch {
    categories.value = [ALL]
  }
}

/**
 * 检索走后端（ES 读模型，ES 不可用由后端自动降级 MySQL）。
 * 关键词与分类是"与"关系：先选分类再搜关键词 = 在该分类内搜索。
 */
async function loadProducts() {
  loading.value = true
  const kw = keyword.value.trim()
  const cat = activeCategory.value
  const browsingAll = !kw && cat === ALL

  try {
    const params = { page: 1, size: PAGE_SIZE }
    if (kw) params.keyword = kw
    if (cat && cat !== ALL) params.category = cat

    const res = await productApi.search(params)
    const list = Array.isArray(res.data) ? res.data : []
    // 只有"浏览全部且后端确实没数据"时才展示示例数据；
    // 搜索无结果必须如实显示"没找到"，否则用户会以为搜到了假商品
    products.value = list.length || !browsingAll ? list : FALLBACK_PRODUCTS
    isDemo.value = !list.length && browsingAll
  } catch {
    products.value = FALLBACK_PRODUCTS
    isDemo.value = true
  } finally {
    loading.value = false
  }
}

function doSearch() {
  loadProducts()
}

function selectCategory(c) {
  if (activeCategory.value === c) return
  activeCategory.value = c
  loadProducts()
}

/** 未登录点击商品 → 引导到登录页（真正的强制拦截在网关） */
function openProduct(product) {
  if (!userStore.isLogin) {
    router.push({ name: 'login', query: { redirect: `/product/${product.id}` } })
    return
  }
  if (typeof product.id === 'string' && product.id.startsWith('demo-')) return
  router.push({ name: 'product-detail', params: { id: product.id } })
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadProducts()])
})
</script>

<template>
  <div class="market container">
    <header class="market-head">
      <div>
        <h1 class="title">市集</h1>
        <p class="sub">慢慢看，不着急</p>
      </div>
      <p class="count">{{ products.length }} 件作品</p>
    </header>

    <!-- 游客浏览提示：可看不可买 -->
    <div v-if="!userStore.isLogin" class="guest-bar">
      <span>你正在以游客身份浏览，登录后可查看作品详情、加入购物车</span>
      <router-link class="btn btn-primary guest-btn" to="/login">去登录</router-link>
    </div>

    <!-- 搜索 -->
    <form class="search-bar" @submit.prevent="doSearch">
      <input
        v-model="keyword"
        class="search-input"
        type="text"
        placeholder="搜索作品名称、分类或它背后的故事…"
      />
      <button class="btn btn-primary search-btn" type="submit">搜索</button>
    </form>

    <!-- 分类（来自后端真实分类） -->
    <div class="chips">
      <button
        v-for="c in categories"
        :key="c"
        :class="['chip', { active: activeCategory === c }]"
        @click="selectCategory(c)"
      >
        {{ c }}
      </button>
    </div>

    <p v-if="isDemo" class="notice">未连接后端，当前展示示例数据</p>

    <div v-if="loading" class="loading">正在拾取作品…</div>

    <div v-else-if="products.length" class="grid">
      <ProductCard v-for="p in products" :key="p.id" :product="p" @select="openProduct" />
    </div>

    <p v-else class="loading">
      {{ keyword.trim() ? `没有找到与「${keyword.trim()}」相关的作品` : '这个分类下暂时还没有作品' }}
    </p>
  </div>
</template>

<style scoped>
.market {
  padding-top: 52px;
}

.market-head {
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
.count {
  font-size: 13px;
  color: var(--c-text-mute);
  letter-spacing: 0.06em;
}

.guest-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  margin-bottom: 24px;
  font-size: 13.5px;
  color: var(--c-text-sub);
  background: var(--c-accent-soft);
  border-radius: var(--r-md);
  border-left: 3px solid var(--c-accent);
}
.guest-btn {
  height: 34px;
  padding: 0 20px;
  font-size: 13.5px;
  flex-shrink: 0;
}

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 22px;
}
.search-input {
  flex: 1;
  height: 42px;
  padding: 0 18px;
  font-size: 14px;
  color: var(--c-text);
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--r-pill);
  transition: border-color 0.28s var(--ease);
}
.search-input:focus {
  outline: none;
  border-color: var(--c-accent);
}
.search-btn {
  height: 42px;
  padding: 0 26px;
  flex-shrink: 0;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 30px;
}
.chip {
  padding: 6px 18px;
  font-size: 13.5px;
  color: var(--c-text-sub);
  background: var(--c-surface);
  border: 1px solid var(--c-line);
  border-radius: var(--r-pill);
  transition: all 0.28s var(--ease);
}
.chip:hover {
  color: var(--c-accent-deep);
  border-color: var(--c-accent);
}
.chip.active {
  color: #fff;
  background: var(--c-accent);
  border-color: var(--c-accent);
  box-shadow: var(--sh-accent);
}

.notice {
  margin-bottom: 18px;
  font-size: 12.5px;
  color: var(--c-accent-deep);
  letter-spacing: 0.04em;
}

.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.loading {
  padding: 70px 0;
  text-align: center;
  color: var(--c-text-mute);
  font-size: 14px;
}

@media (max-width: 1024px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 640px) {
  .grid {
    grid-template-columns: 1fr;
    gap: 18px;
  }
  .market-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
  .guest-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .search-bar {
    flex-wrap: wrap;
  }
  .search-input {
    flex: 1 1 100%;
  }
  .search-btn {
    width: 100%;
  }
  .title {
    font-size: 25px;
  }
}
</style>
