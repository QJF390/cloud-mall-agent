<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()

/** 侧边栏菜单：与 router/index.js 的 group 字段对应 */
const menus = [
  { title: '控制台', path: '/dashboard', children: [] },
  { title: '用户管理', children: [{ title: '用户列表', path: '/user' }] },
  { title: '商品管理', children: [{ title: '商品列表', path: '/product' }] },
  { title: '订单管理', children: [{ title: '订单列表', path: '/order' }] },
  { title: '客服中心', children: [{ title: '客服工作台', path: '/kefu' }] },
  {
    title: '系统管理',
    children: [
      { title: '管理员账号', path: '/system/admin' },
      { title: '角色权限', path: '/system/role' },
      { title: '操作日志', path: '/system/log' },
      { title: '系统配置', path: '/system/config' }
    ]
  }
]

const currentTitle = computed(() => route.meta.title || '控制台')

async function handleLogout() {
  await adminStore.logout()
  router.push({ name: 'admin-login' })
}
</script>

<template>
  <div class="admin-shell">
    <!-- 侧边栏 -->
    <aside class="side">
      <div class="side-logo">巧见 · 后台</div>
      <nav class="side-nav">
        <template v-for="menu in menus" :key="menu.title">
          <div v-if="menu.children.length" class="nav-group">{{ menu.title }}</div>
          <router-link
            v-for="item in menu.children"
            :key="item.path"
            class="nav-item"
            :class="{ active: route.path === item.path }"
            :to="item.path"
          >
            {{ item.title }}
          </router-link>
          <router-link
            v-if="!menu.children.length"
            class="nav-item"
            :class="{ active: route.path === menu.path }"
            :to="menu.path"
          >
            {{ menu.title }}
          </router-link>
        </template>
      </nav>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <header class="topbar">
        <h1 class="topbar-title">{{ currentTitle }}</h1>
        <div class="topbar-user">
          <span>{{ adminStore.nickname || adminStore.username || '未登录' }}</span>
          <button class="btn-link" @click="handleLogout">退出</button>
        </div>
      </header>

      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  min-height: 100vh;
}
.side {
  width: 210px;
  flex-shrink: 0;
  background: var(--c-side);
  color: #cfd6e4;
  padding: 18px 0;
}
.side-logo {
  padding: 0 20px 18px;
  font-size: 16px;
  letter-spacing: 0.16em;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.side-nav {
  padding: 14px 12px;
}
.nav-group {
  margin: 16px 8px 6px;
  font-size: 12px;
  color: #6b7688;
  letter-spacing: 0.1em;
}
.nav-item {
  display: block;
  padding: 9px 12px;
  border-radius: 6px;
  font-size: 14px;
  color: #cfd6e4;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}
.nav-item.active {
  background: var(--c-primary);
  color: #fff;
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--c-bg);
}
.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid var(--c-border);
}
.topbar-title {
  font-size: 16px;
  font-weight: 600;
}
.topbar-user {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 13px;
  color: var(--c-text-sub);
}
.content {
  padding: 20px 24px 40px;
}
</style>
