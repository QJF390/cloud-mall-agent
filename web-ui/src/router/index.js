import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    // bare：通栏 Hero，导航透明悬浮、不留导航高度
    meta: { title: '巧见 · 手作市集', bare: true }
  },
  {
    path: '/products',
    name: 'products',
    component: () => import('@/views/ProductListView.vue'),
    meta: { title: '市集' }
  },
  {
    path: '/ai',
    name: 'ai-chat',
    component: () => import('@/views/AiChatView.vue'),
    // 游客也能问（ai-service 会按 satoken 是否存在决定能否调身份相关工具）
    meta: { title: '巧见助手' }
  },
  {
    path: '/product/:id',
    name: 'product-detail',
    component: () => import('@/views/ProductDetailView.vue'),
    // 商品详情需登录：前端守卫先引导，真正的强制拦截在网关
    meta: { title: '作品详情', requiresAuth: true }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录 / 注册' }
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { title: '消息', requiresAuth: true }
  },
  {
    path: '/cart',
    name: 'cart',
    component: () => import('@/views/CartView.vue'),
    meta: { title: '购物车', requiresAuth: true }
  },
  {
    path: '/checkout',
    name: 'checkout',
    component: () => import('@/views/CheckoutView.vue'),
    meta: { title: '确认订单', requiresAuth: true }
  },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('@/views/OrderView.vue'),
    meta: { title: '我的订单', requiresAuth: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  document.title = to.meta.title || '巧见 · 手作市集'

  // 需要登录的页面：本地无 token 时直接引导登录（体验层）
  // 注意这不等于安全 —— 即便绕过此处，网关也会用 401 拦住接口
  if (to.meta.requiresAuth && !getToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
