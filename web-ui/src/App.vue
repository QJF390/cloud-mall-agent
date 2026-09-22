<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SiteHeader from '@/components/SiteHeader.vue'
import SiteFooter from '@/components/SiteFooter.vue'

const route = useRoute()
// 首页是通栏 Hero：导航透明浮在图上、不留导航高度
const bare = computed(() => route.meta.bare === true)
</script>

<template>
  <SiteHeader :transparent="bare" />

  <main :class="['app-main', { 'app-main--bare': bare }]">
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </main>

  <SiteFooter />
</template>

<style scoped>
.app-main {
  min-height: 60vh;
  padding-top: var(--nav-h);
}
/* 通栏页（首页）：Hero 从屏幕顶部开始，导航覆盖其上 */
.app-main--bare {
  padding-top: 0;
}
</style>
