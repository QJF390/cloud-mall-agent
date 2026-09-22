import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

/**
 * 后台路由。
 * - /login 独立布局（meta.blank）
 * - 其余页面共用一个 AdminLayout 框架（侧边栏 + 顶栏）
 * 真正的权限校验在网关与 admin-service，这里只做体验层拦截。
 */
const routes = [
  {
    path: '/login',
    name: 'admin-login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '管理员登录', blank: true }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/DashboardView.vue'),
    meta: { title: '控制台', icon: 'dashboard', requiresAuth: true }
  },
  // ==================== 用户管理 ====================
  {
    path: '/user',
    name: 'user-manage',
    component: () => import('@/views/user/UserManageView.vue'),
    meta: { title: '用户管理', group: '用户管理', requiresAuth: true }
  },
  // ==================== 商品管理 ====================
  {
    path: '/product',
    name: 'product-manage',
    component: () => import('@/views/product/ProductManageView.vue'),
    meta: { title: '商品管理', group: '商品管理', requiresAuth: true }
  },
  // ==================== 订单管理 ====================
  {
    path: '/order',
    name: 'order-manage',
    component: () => import('@/views/order/OrderManageView.vue'),
    meta: { title: '订单管理', group: '订单管理', requiresAuth: true }
  },
  // ==================== 客服工作台 ====================
  {
    path: '/kefu',
    name: 'kefu',
    component: () => import('@/views/KeFuView.vue'),
    meta: { title: '客服工作台', group: '客服中心', requiresAuth: true }
  },
  // ==================== 系统管理 ====================
  {
    path: '/system/admin',
    name: 'system-admin',
    component: () => import('@/views/system/AdminAccountView.vue'),
    meta: { title: '管理员账号', group: '系统管理', requiresAuth: true }
  },
  {
    path: '/system/role',
    name: 'system-role',
    component: () => import('@/views/system/RoleManageView.vue'),
    meta: { title: '角色权限', group: '系统管理', requiresAuth: true }
  },
  {
    path: '/system/log',
    name: 'system-log',
    component: () => import('@/views/system/LogManageView.vue'),
    meta: { title: '操作日志', group: '系统管理', requiresAuth: true }
  },
  {
    path: '/system/config',
    name: 'system-config',
    component: () => import('@/views/system/ConfigManageView.vue'),
    meta: { title: '系统配置', group: '系统管理', requiresAuth: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 巧见后台` : '巧见 · 后台管理'
  if (to.meta.requiresAuth && !getToken()) {
    return { name: 'admin-login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
