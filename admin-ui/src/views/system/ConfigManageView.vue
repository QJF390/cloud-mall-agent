<script setup>
import { onMounted, ref } from 'vue'
import { adminSystemApi } from '@/api/modules'
import { toast } from '@/utils/toast'

/** 系统管理 · 系统配置：按分组查询并修改 */
const group = ref('')
const list = ref([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await adminSystemApi.configList(group.value || undefined)
    list.value = res.data || []
  } catch (e) {
    list.value = []
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

async function handleUpdate(row) {
  try {
    await adminSystemApi.updateConfig(row.configKey, row.configValue ?? '')
    toast.success('配置已保存')
  } catch (e) {
    toast.error(e.message)
    // 保存失败时回滚为后端当前值，避免界面显示与库里不一致
    loadList()
  }
}

onMounted(loadList)
</script>

<template>
  <div class="card">
    <div class="page-toolbar">
      <select v-model="group" class="select">
        <option value="">全部分组</option>
        <option value="ORDER">订单</option>
        <option value="USER">用户</option>
        <option value="PRODUCT">商品</option>
        <option value="SYSTEM">系统</option>
      </select>
      <button class="btn btn-primary" @click="loadList">查询</button>
      <span class="hint">提示：配置仅允许修改已有配置项，新增配置请走 SQL 初始化脚本。</span>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>配置键</th>
          <th>配置值</th>
          <th>分组</th>
          <th>说明</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in list" :key="row.configKey">
          <td>{{ row.configKey }}</td>
          <td>
            <input v-model="row.configValue" class="input value-input" />
          </td>
          <td>{{ row.configGroup || '—' }}</td>
          <td>{{ row.remark || '—' }}</td>
          <td>
            <button class="btn-link" @click="handleUpdate(row)">保存</button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="!list.length && !loading" class="empty">暂无配置项（请先执行 sql/admin-schema.sql 初始化）</p>
  </div>
</template>

<style scoped>
.value-input {
  width: 100%;
  max-width: 320px;
}
.hint {
  font-size: 12px;
  color: var(--c-text-sub);
}
</style>
