<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  // 首页通栏 Hero：导航初始透明浮在图片上
  transparent: { type: Boolean, default: false }
})

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const scrolled = ref(false)
const menuOpen = ref(false)

const solid = computed(() => !props.transparent || scrolled.value)

const navs = [
  { name: 'home', label: '首页' },
  { name: 'products', label: '市集' },
  { name: 'ai-chat', label: '巧见助手' },
  { name: 'chat', label: '消息' },
  { name: 'cart', label: '购物车' },
  { name: 'orders', label: '我的订单' }
]

function isActive(name) {
  if (name === 'products') {
    return route.name === 'products' || route.name === 'product-detail'
  }
  return route.name === name
}

function onScroll() {
  scrolled.value = window.scrollY > 24
}

function go(name) {
  menuOpen.value = false
  router.push({ name })
}

function onLogout() {
  userStore.logout()
  menuOpen.value = false
  router.push({ name: 'home' })
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <header :class="['site-header', { 'is-solid': solid, 'is-open': menuOpen }]">
    <div class="header-inner container">
      <router-link class="logo" to="/" @click="menuOpen = false">
        <span class="logo-dot"></span>
        <span class="logo-text">巧见</span>
      </router-link>

      <nav class="nav-desktop">
        <button
          v-for="n in navs"
          :key="n.name"
          :class="['nav-link', { active: isActive(n.name) }]"
          @click="go(n.name)"
        >
          {{ n.label }}
        </button>
      </nav>

      <div class="header-right">
        <template v-if="userStore.isLogin">
          <span class="user-name">{{ userStore.username }}</span>
          <button class="btn-plain logout" @click="onLogout">退出</button>
        </template>
        <router-link v-else class="btn btn-ghost login-btn" to="/login">登录</router-link>

        <button class="burger" @click="menuOpen = !menuOpen" aria-label="菜单">
          <span></span><span></span><span></span>
        </button>
      </div>
    </div>

    <!-- 移动端抽屉 -->
    <transition name="drawer">
      <nav v-if="menuOpen" class="nav-mobile">
        <button
          v-for="n in navs"
          :key="n.name"
          :class="['nav-link', { active: isActive(n.name) }]"
          @click="go(n.name)"
        >
          {{ n.label }}
        </button>
        <button v-if="userStore.isLogin" class="nav-link" @click="onLogout">退出登录</button>
        <button v-else class="nav-link" @click="go('login')">登录 / 注册</button>
      </nav>
    </transition>
  </header>
</template>

<style scoped>
.site-header {
  position: fixed;
  inset: 0 0 auto 0;
  z-index: 100;
  height: var(--nav-h);
  transition: background 0.4s var(--ease), box-shadow 0.4s var(--ease);
}
.site-header.is-solid {
  background: rgba(243, 249, 252, 0.85);
  backdrop-filter: saturate(180%) blur(14px);
  box-shadow: var(--sh-nav);
}

.header-inner {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 9px;
}

/* 字标：与全站标题统一的衬线文艺体，暖墨渐变替代纯黑，去掉方正死板感 */
.logo-text {
  font-family: var(--font-serif);
  font-size: 21px;
  font-weight: 600;
  letter-spacing: 0.16em;
  /* 衬线体字距会在末字右侧留下空白，向回收一点让视觉居中 */
  margin-right: -0.16em;
  /* 暖墨渐变：深青灰 → 青蓝 → 天空蓝，像水彩在天色里晕开 */
  background: linear-gradient(115deg, var(--c-text) 0%, #4a7f96 55%, var(--c-accent) 100%);
  background-size: 220% 100%;
  background-position: 0% 0;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
  transition: background-position 0.6s var(--ease);
}
.logo:hover .logo-text {
  background-position: 100% 0;
}
.logo-dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  border: 2.5px solid var(--c-sun);
  transition: transform 0.5s var(--ease);
}
.logo:hover .logo-dot {
  transform: scale(1.35) rotate(90deg);
}

.nav-desktop {
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-link {
  position: relative;
  padding: 6px 14px;
  font-size: 14.5px;
  color: var(--c-text-sub);
  border-radius: var(--r-pill);
  transition: color 0.25s var(--ease), background 0.25s var(--ease);
}
.nav-link:hover {
  color: var(--c-text);
  background: rgba(62, 143, 176, 0.1);
}
.nav-link.active {
  color: var(--c-accent-deep);
}
.nav-link.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 1px;
  width: 16px;
  height: 2px;
  border-radius: 2px;
  background: var(--c-accent);
  transform: translateX(-50%);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-name {
  font-size: 14px;
  color: var(--c-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.logout {
  font-size: 13.5px;
}
.login-btn {
  height: 36px;
  padding: 0 20px;
  font-size: 14px;
}

/* 汉堡按钮：移动端才出现 */
.burger {
  display: none;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  width: 34px;
  height: 34px;
  padding: 0 6px;
}
.burger span {
  display: block;
  height: 1.6px;
  background: var(--c-text);
  border-radius: 2px;
  transition: transform 0.3s var(--ease);
}

.nav-mobile {
  display: none;
  flex-direction: column;
  padding: 8px 20px 18px;
  background: rgba(243, 249, 252, 0.98);
  backdrop-filter: blur(14px);
  box-shadow: var(--sh-card);
}
.nav-mobile .nav-link {
  text-align: left;
  padding: 12px 8px;
  border-radius: var(--r-sm);
  font-size: 15px;
}

.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.25s var(--ease), transform 0.25s var(--ease);
}
.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 768px) {
  .nav-desktop {
    display: none;
  }
  .burger {
    display: flex;
  }
  .nav-mobile {
    display: flex;
  }
  .user-name {
    display: none;
  }
}
</style>
