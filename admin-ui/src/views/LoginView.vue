<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function handleSubmit() {
  error.value = ''
  if (!form.username || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    await adminStore.login({ ...form })
    router.replace(route.query.redirect || '/dashboard')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="handleSubmit">
      <h1 class="login-title">巧见 · 后台管理</h1>
      <p class="login-sub">Admin Console</p>

      <label class="field">
        <span>用户名</span>
        <input v-model.trim="form.username" class="input" autocomplete="username" />
      </label>

      <label class="field">
        <span>密码</span>
        <input
          v-model="form.password"
          class="input"
          type="password"
          autocomplete="current-password"
        />
      </label>

      <p v-if="error" class="login-error">{{ error }}</p>

      <button class="btn btn-primary login-btn" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登 录' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2533, #2b3a5c);
}
.login-card {
  width: 340px;
  padding: 32px 28px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
}
.login-title {
  font-size: 20px;
  letter-spacing: 0.1em;
}
.login-sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--c-text-sub);
  letter-spacing: 0.24em;
}
.field {
  display: block;
  margin-top: 18px;
}
.field span {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--c-text-sub);
}
.field .input {
  width: 100%;
  height: 38px;
}
.login-error {
  margin-top: 12px;
  font-size: 13px;
  color: var(--c-danger);
}
.login-btn {
  width: 100%;
  height: 40px;
  margin-top: 22px;
}
</style>
