<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { productApi } from '@/api/modules'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()

/**
 * 兜底示例数据：后端未启动时首页依然完整可看。
 * 图片与 sql/insert-sample-products.sql 中的路径保持一致。
 */
const FALLBACK_PRODUCTS = [
  {
    id: 'demo-1',
    name: '粗陶手作咖啡杯',
    category: '陶瓷',
    story: '泥土在指尖成型，窑火赋予它温度。这一杯，盛的是晨光。',
    price: 128,
    stock: 10,
    coverImage: '/products/ceramic_coffee_cup.png'
  },
  {
    id: 'demo-2',
    name: '植物染亚麻帆布袋',
    category: '布艺',
    story: '用板蓝根与栀子煮出的颜色，会随使用慢慢褪色，留下你的痕迹。',
    price: 168,
    stock: 8,
    coverImage: '/products/linen_tote_bag.png'
  },
  {
    id: 'demo-3',
    name: '手工银质细戒指',
    category: '饰品',
    story: '银会记住你佩戴的每一天，慢慢氧化成只属于你的光泽。',
    price: 89,
    stock: 15,
    coverImage: '/products/silver_ring.png'
  },
  {
    id: 'demo-4',
    name: '黑胡桃木手机支架',
    category: '木作',
    story: '从一块边角料开始，打磨出适合手掌的弧度。',
    price: 118,
    stock: 12,
    coverImage: '/products/walnut_phone_stand.png'
  }
]

const products = ref([])
const isDemo = ref(false)
const parX = ref(0)
const parY = ref(0)

/** 三特点卡片数据（图片为氛围示意，不必与文案一一对应） */
const FEATURES = [
  {
    no: '01',
    title: '手作的温度',
    desc: '孤品与限量款为主，买到的不是货，是别人花过的时间。',
    img: '/products/ceramic_coffee_cup.png'
  },
  {
    no: '02',
    title: '担保交易',
    desc: '付款先冻结在平台，确认收货后才完成结算，交易更安心。',
    img: '/products/silver_ring.png'
  },
  {
    no: '03',
    title: '慢一点也没关系',
    desc: '不催促、不凑单。喜欢就收下，不喜欢就当作逛了一趟市集。',
    img: '/products/linen_tote_bag.png'
  }
]

const parallaxStyle = computed(() => ({
  transform: `translate3d(${parX.value}px, ${parY.value}px, 0)`
}))

function onMouseMove(e) {
  const w = window.innerWidth
  const h = window.innerHeight
  parX.value = (e.clientX / w - 0.5) * -18
  parY.value = (e.clientY / h - 0.5) * -14
}

async function loadProducts() {
  try {
    const res = await productApi.list()
    const list = Array.isArray(res.data) ? res.data : []
    if (list.length) {
      products.value = list.slice(0, 5)
    } else {
      products.value = FALLBACK_PRODUCTS
      isDemo.value = true
    }
  } catch {
    products.value = FALLBACK_PRODUCTS
    isDemo.value = true
  }
  await nextTick()
  observeReveal()
}

function enterMarket() {
  router.push({ name: 'products' })
}

/** 点击卡片：未登录时由路由守卫引导登录（网关也会兜底拦截） */
function openProduct(product) {
  if (typeof product.id === 'string' && product.id.startsWith('demo-')) {
    router.push({ name: 'products' })
    return
  }
  router.push({ name: 'product-detail', params: { id: product.id } })
}

function scrollToRecommend() {
  document.getElementById('recommend')?.scrollIntoView({ behavior: 'smooth' })
}

let observer = null
function observeReveal() {
  const els = document.querySelectorAll('.home .reveal:not(.is-visible)')
  if (!('IntersectionObserver' in window)) {
    els.forEach((el) => el.classList.add('is-visible'))
    return
  }
  if (!observer) {
    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible')
            observer.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' }
    )
  }
  els.forEach((el) => observer.observe(el))
}

onMounted(() => {
  loadProducts()
  window.addEventListener('mousemove', onMouseMove, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  observer?.disconnect()
})
</script>

<template>
  <div class="home">
    <!-- ==================== Hero ==================== -->
    <section class="hero">
      <div class="hero-bg"></div>
      <div class="hero-veil"></div>

      <div class="hero-inner container" :style="parallaxStyle">
        <p class="hero-kicker">巧见 · 手作市集</p>
        <h1 class="hero-title">
          <span class="line line-1">把慢下来的时光</span>
          <span class="line line-2">酿成手作的形状</span>
        </h1>
        <p class="hero-sub line line-3">
          一个收集手作、植物与故事的小市集<br />
          每一件，都带着制作它的人的呼吸
        </p>

        <div class="hero-actions line line-4">
          <button class="btn btn-primary" @click="enterMarket">进入市集</button>
          <button class="btn btn-ghost hero-ghost" @click="scrollToRecommend">
            先听一段故事
          </button>
        </div>
      </div>

      <button class="hero-scroll" @click="scrollToRecommend" aria-label="向下滚动">
        <span class="scroll-text">向下</span>
        <span class="scroll-line"></span>
      </button>
    </section>

    <!-- ==================== 推荐 ==================== -->
    <section id="recommend" class="section container">
      <header class="section-head reveal">
        <h2 class="section-title">今日 · 巧见</h2>
        <p class="section-sub">
          每一件，都带着制作它的人的呼吸
          <span v-if="isDemo" class="demo-hint">（示例数据 · 后端未连接）</span>
        </p>
      </header>

      <div v-if="products.length" class="grid">
        <ProductCard
          v-for="(p, i) in products"
          :key="p.id"
          :product="p"
          :large="i === 0"
          :class="['reveal', { 'span-2': i === 0 }]"
          @select="openProduct"
        />
      </div>
      <p v-else class="empty">正在拾取今日的作品…</p>
    </section>

    <!-- ==================== 三特点（图片卡 · 参考 ONE 精品服务） ==================== -->
    <section class="section section--deep">
      <div class="container">
        <header class="section-head head-center reveal">
          <h2 class="section-title">巧见 · 三件事</h2>
          <p class="section-sub">关于慢，关于信任，关于手作</p>
        </header>

        <ul class="features">
          <li
            v-for="f in FEATURES"
            :key="f.no"
            class="feature-card reveal"
            @click="enterMarket"
          >
            <div class="fc-media">
              <img :src="f.img" :alt="f.title" loading="lazy" />
            </div>
            <div class="fc-body">
              <span class="f-no">{{ f.no }}</span>
              <h3 class="f-title">{{ f.title }}</h3>
              <p class="f-desc">{{ f.desc }}</p>
              <span class="f-more">看看作品</span>
            </div>
          </li>
        </ul>
      </div>
    </section>

    <!-- ==================== 底部 CTA ==================== -->
    <section class="cta container reveal">
      <p class="cta-text">逛逛看，也许正好有一件在等你。</p>
      <button class="btn btn-primary" @click="enterMarket">进入市集</button>
    </section>
  </div>
</template>

<style scoped>
/* ==================== Hero ==================== */
.hero {
  position: relative;
  height: 100vh;
  min-height: 560px;
  overflow: hidden;
  display: flex;
  align-items: center;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background: url('/products/hero.png') center / cover no-repeat;
  animation: kenburns 26s ease-in-out infinite alternate;
  will-change: transform;
}
@keyframes kenburns {
  from {
    transform: scale(1.06) translate3d(0, 0, 0);
  }
  to {
    transform: scale(1.17) translate3d(-2%, -1.6%, 0);
  }
}

.hero-veil {
  position: absolute;
  inset: 0;
  /* 全程天空暖白压暗左缘、渐隐向右，让暖木底图自己呼吸；
     不掺阳光黄 —— 黄叠在暖色照片上会发闷发脏 */
  background: linear-gradient(
    100deg,
    rgba(243, 249, 252, 0.95) 0%,
    rgba(243, 249, 252, 0.74) 42%,
    rgba(243, 249, 252, 0.22) 72%,
    rgba(243, 249, 252, 0.05) 100%
  );
}

.hero-inner {
  position: relative;
  z-index: 2;
  transition: transform 0.7s var(--ease);
}

.hero-kicker {
  font-size: 13px;
  letter-spacing: 0.34em;
  color: var(--c-accent-deep);
  animation: fadeUp 0.9s var(--ease) 0.1s both;
}

.hero-title {
  margin-top: 20px;
  font-family: var(--font-serif);
  font-size: clamp(30px, 5.4vw, 60px);
  font-weight: 500;
  line-height: 1.34;
  letter-spacing: 0.06em;
}
.hero-title .line {
  display: block;
}
.line-1 {
  animation: fadeUp 0.9s var(--ease) 0.24s both;
}
.line-2 {
  animation: fadeUp 0.9s var(--ease) 0.4s both;
}
.line-2::after {
  content: '';
  display: inline-block;
  width: 46px;
  height: 3px;
  margin-left: 14px;
  vertical-align: middle;
  border-radius: 3px;
  background: var(--c-sun);
}

.hero-sub {
  margin-top: 22px;
  max-width: 460px;
  font-size: 15px;
  line-height: 2.1;
  color: var(--c-text-sub);
}
.line-3 {
  animation: fadeUp 0.9s var(--ease) 0.56s both;
}
.line-4 {
  animation: fadeUp 0.9s var(--ease) 0.7s both;
}

.hero-actions {
  margin-top: 36px;
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}
.hero-ghost {
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(6px);
}

.hero-scroll {
  position: absolute;
  left: 50%;
  bottom: 34px;
  z-index: 2;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.scroll-text {
  font-size: 12px;
  letter-spacing: 0.3em;
  color: var(--c-text-sub);
  padding-left: 0.3em;
}
.scroll-line {
  width: 1.5px;
  height: 42px;
  background: linear-gradient(var(--c-sun), transparent);
  animation: breathe 2.2s var(--ease) infinite;
}

@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(24px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes breathe {
  0%,
  100% {
    opacity: 0.25;
    transform: scaleY(0.7);
  }
  50% {
    opacity: 1;
    transform: scaleY(1);
  }
}

/* ==================== 区块 ==================== */
.section {
  padding: 92px 0;
}
.section--deep {
  background: var(--c-bg-deep);
}
.section-head {
  margin-bottom: 38px;
}
.section-title {
  font-family: var(--font-serif);
  font-size: 26px;
  font-weight: 500;
  letter-spacing: 0.16em;
}
.section-sub {
  margin-top: 10px;
  font-size: 13.5px;
  color: var(--c-text-mute);
  letter-spacing: 0.06em;
}
.head-center {
  text-align: center;
}
.demo-hint {
  color: var(--c-accent-deep);
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 26px;
}
.grid :deep(.span-2) {
  grid-column: span 2;
}

.empty {
  padding: 40px 0;
  text-align: center;
  color: var(--c-text-mute);
  font-size: 14px;
}

/* ==================== 三特点（图片卡） ==================== */
/* 参考 ONE「精品服务」：三卡紧挨，图片通栏在上，白色页脚居中，hover 整卡抬升 */
.features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px; /* 极窄缝透出底色，形成参考图那种「拼贴」感 */
}
.feature-card {
  background: var(--c-surface);
  cursor: pointer;
  transition:
    transform 0.45s var(--ease),
    box-shadow 0.45s var(--ease);
}
.feature-card:hover {
  transform: translateY(-10px);
  box-shadow: var(--sh-hover);
  z-index: 1; /* 抬升时压在相邻卡之上 */
}
.fc-media {
  aspect-ratio: 3 / 2;
  overflow: hidden;
}
.fc-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.6s var(--ease);
}
.feature-card:hover .fc-media img {
  transform: scale(1.05);
}
.fc-body {
  padding: 30px 26px 34px;
  text-align: center;
}
.f-no {
  font-family: var(--font-serif);
  font-size: 13px;
  color: var(--c-sun-deep);
  letter-spacing: 0.24em;
}
.f-title {
  margin-top: 10px;
  font-family: var(--font-serif);
  font-size: 19px;
  font-weight: 500;
  letter-spacing: 0.12em;
}
.f-desc {
  margin-top: 12px;
  font-size: 13.5px;
  line-height: 1.95;
  color: var(--c-text-sub);
  max-width: 260px;
  margin-left: auto;
  margin-right: auto;
}
.f-more {
  display: inline-block;
  margin-top: 18px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.32em;
  padding-left: 0.32em; /* 抵消最后一个字的字距，视觉居中 */
  color: var(--c-accent);
  transition: color 0.3s var(--ease);
}
.feature-card:hover .f-more {
  color: var(--c-accent-deep);
}

/* ==================== CTA ==================== */
.cta {
  padding: 90px 24px;
  text-align: center;
}
.cta-text {
  font-family: var(--font-serif);
  font-size: 21px;
  letter-spacing: 0.14em;
  color: var(--c-text);
  margin-bottom: 26px;
}

/* ==================== 响应式 ==================== */
@media (max-width: 768px) {
  .hero {
    height: 92vh;
  }
  .hero-veil {
    background: linear-gradient(
      180deg,
      rgba(243, 249, 252, 0.92) 0%,
      rgba(243, 249, 252, 0.8) 55%,
      rgba(243, 249, 252, 0.92) 100%
    );
  }
  .line-2::after {
    display: none;
  }
  .grid {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .grid :deep(.span-2) {
    grid-column: span 1;
  }
  .features {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .feature-card {
    cursor: default;
  }
  .section {
    padding: 62px 0;
  }
  .cta {
    padding: 60px 24px;
  }
}
</style>
