<script setup>
import { onMounted, reactive, ref } from 'vue'
import { adminSystemApi } from '@/api/modules'
import { toast } from '@/utils/toast'
import PaginationBar from '@/components/PaginationBar.vue'

/** 系统管理 · 操作日志（审计） */
const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', startTime: '', endTime: '' })
const list = ref([])
const total = ref(0)
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.keyword) params.keyword = query.keyword
    if (query.startTime) params.startTime = query.startTime
    if (query.endTime) params.endTime = query.endTime
    const res = await adminSystemApi.logPage(params)
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

function handleReset() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: query.pageSize,
    keyword: '',
    startTime: '',
    endTime: ''
  })
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

onMounted(loadList)
</script>

<template>
  <div class="card">
    <div class="page-toolbar">
      <input
        v-model.trim="query.keyword"
        class="input"
        placeholder="操作人 / 模块 / 操作"
        @keyup.enter="handleSearch"
      />
      <input v-model="query.startTime" class="input" type="date" />
      <span class="range-sep">至</span>
      <input v-model="query.endTime" class="input" type="date" />
      <button class="btn btn-primary" @click="handleSearch">查询</button>
      <button class="btn" @click="handleReset">重置</button>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>时间</th>
          <th>操作人</th>
          <th>模块</th>
          <th>操作</th>
          <th>接口</th>
          <th>结果</th>
          <th>耗时</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in list" :key="row.id">
          <td>{{ row.createTime }}</td>
          <td>{{ row.operator || '—' }}</td>
          <td>{{ row.module }}</td>
          <td>{{ row.action }}</td>
          <td class="uri-cell" :title="row.requestUri">{{ row.requestUri }}</td>
          <td>
            <span :class="row.success === 1 ? 'tag tag-success' : 'tag tag-danger'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </span>
            <span v-if="row.success !== 1 && row.errorMsg" class="err" :title="row.errorMsg">
              {{ row.errorMsg }}
            </span>
          </td>
          <td>{{ row.costMs }}ms</td>
        </tr>
      </tbody>
    </table>

    <p v-if="!list.length && !loading" class="empty">暂无操作日志</p>

    <PaginationBar
      :page="query.pageNum"
      :size="query.pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      @change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped>
.range-sep {
  color: var(--c-text-sub);
  font-size: 13px;
}
.uri-cell {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-text-sub);
}
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
.err {
  display: inline-block;
  max-width: 200px;
  margin-left: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: var(--c-danger);
  font-size: 12px;
}
</style>
