<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '@/layout/AdminLayout.vue'
import AppToast from '@/components/AppToast.vue'

const route = useRoute()
// 登录页等不需要后台框架布局
const blank = computed(() => route.meta.blank === true)
</script>

<template>
  <router-view v-if="blank" />
  <AdminLayout v-else>
    <router-view />
  </AdminLayout>
  <AppToast />
</template>
