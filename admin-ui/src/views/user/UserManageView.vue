<script setup>
import { onMounted, reactive, ref } from 'vue'
import { adminUserApi } from '@/api/modules'
import { toast } from '@/utils/toast'
import PaginationBar from '@/components/PaginationBar.vue'

/** 用户管理：列表 / 搜索 / 禁用启用 */
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: null })
const list = ref([])
const total = ref(0)
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.keyword) params.keyword = query.keyword
    if (query.status !== null) params.status = query.status
    const res = await adminUserApi.page(params)
    list.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (e) {
    list.value = []
    total.value = 0
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handlePageChange(page) {
  query.pageNum = page
  loadList()
}

function handleSizeChange(size) {
  query.pageSize = size
  query.pageNum = 1
  loadList()
}

async function handleDisable(row) {
  if (!window.confirm(`确认禁用用户「${row.username}」？禁用后该用户将无法登录。`)) return
  try {
    await adminUserApi.disable(row.id)
    toast.success('用户已禁用')
    loadList()
  } catch (e) {
    toast.error(e.message)
  }
}

async function handleEnable(row) {
  try {
    await adminUserApi.enable(row.id)
    toast.success('用户已启用')
    loadList()
  } catch (e) {
    toast.error(e.message)
  }
}

onMounted(loadList)
</script>

<template>
  <div class="card">
    <div class="page-toolbar">
      <input
        v-model.trim="query.keyword"
        class="input"
        placeholder="用户名 / 手机号"
        @keyup.enter="handleSearch"
      />
      <select v-model="query.status" class="select">
        <option :value="null">全部状态</option>
        <option :value="1">正常</option>
        <option :value="0">已禁用</option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">查询</button>
      <button class="btn" :disabled="loading" @click="loadList">
        {{ loading ? '加载中…' : '刷新' }}
      </button>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>用户名</th>
          <th>手机号</th>
          <th>信用分</th>
          <th>注册时间</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in list" :key="row.id">
          <td>{{ row.id }}</td>
          <td>{{ row.username }}</td>
          <td>{{ row.phone || '—' }}</td>
          <td>{{ row.creditScore ?? '—' }}</td>
          <td>{{ row.createTime || '—' }}</td>
          <td>
            <span :class="row.status === 0 ? 'tag tag-danger' : 'tag tag-success'">
              {{ row.status === 0 ? '已禁用' : '正常' }}
            </span>
          </td>
          <td>
            <button v-if="row.status === 0" class="btn-link" @click="handleEnable(row)">
              启用
            </button>
            <button v-else class="btn-link btn-link-danger" @click="handleDisable(row)">
              禁用
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="!list.length && !loading" class="empty">暂无用户数据</p>

    <PaginationBar
      :page="query.pageNum"
      :size="query.pageSize"
      :total="total"
      @change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped>
.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.tag-success {
  background: rgba(23, 166, 115, 0.12);
  color: var(--c-success);
}
.tag-danger {
  background: rgba(229, 72, 77, 0.12);
  color: var(--c-danger);
}
.btn-link-danger {
  color: var(--c-danger);
}
</style>
