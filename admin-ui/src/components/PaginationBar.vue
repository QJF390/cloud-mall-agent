<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  total: { type: Number, default: 0 },
  pageSizes: { type: Array, default: () => [10, 20, 50] }
})

const emit = defineEmits(['change', 'size-change'])

const pageCount = computed(() => {
  const count = Math.ceil(props.total / props.size)
  return count > 0 ? count : 1
})

/** 页码窗口：首尾页 + 当前页前后各 2 页，中间用省略号占位 */
const pages = computed(() => {
  const count = pageCount.value
  const current = props.page
  if (count <= 7) {
    return Array.from({ length: count }, (_, i) => ({ page: i + 1 }))
  }
  const wanted = new Set([1, count, current - 2, current - 1, current, current + 1, current + 2])
  const valid = [...wanted].filter((p) => p >= 1 && p <= count).sort((a, b) => a - b)
  const result = []
  let prev = 0
  for (const p of valid) {
    if (prev && p - prev > 1) result.push({ ellipsis: true, key: `gap-${prev}` })
    result.push({ page: p })
    prev = p
  }
  return result
})

function go(target) {
  if (target < 1 || target > pageCount.value || target === props.page) return
  emit('change', target)
}

function onSizeChange(event) {
  emit('size-change', Number(event.target.value))
}
</script>

<template>
  <div class="pager">
    <span class="pager-total">共 {{ total }} 条</span>

    <div class="pager-btns">
      <button class="pager-btn" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
      <template v-for="(item, index) in pages" :key="item.page ?? item.key ?? index">
        <span v-if="item.ellipsis" class="pager-ellipsis">…</span>
        <button
          v-else
          class="pager-btn"
          :class="{ active: item.page === page }"
          @click="go(item.page)"
        >
          {{ item.page }}
        </button>
      </template>
      <button class="pager-btn" :disabled="page >= pageCount" @click="go(page + 1)">下一页</button>
    </div>

    <select class="select pager-size" :value="size" @change="onSizeChange">
      <option v-for="s in pageSizes" :key="s" :value="s">{{ s }} 条/页</option>
    </select>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
  font-size: 13px;
  color: var(--c-text-sub);
}
.pager-btns {
  display: flex;
  align-items: center;
  gap: 6px;
}
.pager-btn {
  min-width: 32px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  background: #fff;
  color: var(--c-text);
  font-size: 13px;
  cursor: pointer;
}
.pager-btn:disabled {
  color: #b8c0cf;
  cursor: not-allowed;
}
.pager-btn.active {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
}
.pager-ellipsis {
  padding: 0 2px;
}
.pager-size {
  height: 30px;
}
</style>
