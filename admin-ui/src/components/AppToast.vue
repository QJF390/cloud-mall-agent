<script setup>
import { toasts, removeToast } from '@/utils/toast'
</script>

<template>
  <teleport to="body">
    <div class="toast-wrap">
      <div
        v-for="item in toasts"
        :key="item.id"
        class="toast"
        :class="`toast-${item.type}`"
        @click="removeToast(item.id)"
      >
        {{ item.message }}
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.toast-wrap {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  pointer-events: none;
}
.toast {
  min-width: 200px;
  max-width: 420px;
  padding: 10px 16px;
  border-radius: 6px;
  font-size: 13px;
  color: #fff;
  box-shadow: 0 8px 24px rgba(31, 37, 51, 0.22);
  pointer-events: auto;
  cursor: pointer;
  word-break: break-all;
}
.toast-success {
  background: var(--c-success);
}
.toast-error {
  background: var(--c-danger);
}
.toast-info {
  background: var(--c-primary);
}
</style>
