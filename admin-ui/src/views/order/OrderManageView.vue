<script setup>
import { onMounted, reactive, ref } from 'vue'
import { adminOrderApi } from '@/api/modules'
import { toast } from '@/utils/toast'
import PaginationBar from '@/components/PaginationBar.vue'

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: null,
  startTime: '',
  endTime: ''
})
const list = ref([])
const total = ref(0)
const loading = ref(false)

const statusOptions = [
  { value: 'PENDING', label: '待付款' },
  { value: 'FROZEN', label: '已付款' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'RECEIVED', label: '已收货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDED', label: '已退款' }
]

const statusMap = Object.fromEntries(statusOptions.map((s) => [s.value, s.label]))

/** 可强制关单：待付款 / 已付款（未发货） */
function canCancel(row) {
  return row.status === 'PENDING' || row.status === 'FROZEN'
}

/** 可发货：已付款（资金已冻结，等待平台发货） */
function canShip(row) {
  return row.status === 'FROZEN'
}

/** 可退款：已发货（资金仍在冻结池） */
function canRefund(row) {
  return row.status === 'SHIPPED'
}

async function loadList() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.keyword) params.keyword = query.keyword
    if (query.status) params.statusText = query.status
    if (query.startTime) params.startTime = query.startTime
    if (query.endTime) params.endTime = query.endTime
    const res = await adminOrderApi.page(params)
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
    status: null,
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

async function handleShip(row) {
  if (!window.confirm(`确认对订单 ${row.orderNo} 执行发货？\n发货后订单进入「已发货」，买家可查看物流轨迹。`)) return
  try {
    await adminOrderApi.ship(row.id)
    toast.success('发货成功')
    loadList()
  } catch (e) {
    toast.error(e.message)
  }
}

async function handleCancel(row) {
  if (!window.confirm(`确认对订单 ${row.orderNo} 强制关单？\n将解冻买家资金并回补库存。`)) return
  const reason = window.prompt('请输入关单原因（必填，会记录到操作日志）', '')
  if (reason === null) return
  if (!reason.trim()) {
    toast.error('请填写关单原因')
    return
  }
  try {
    await adminOrderApi.cancel(row.id, reason.trim())
    toast.success('关单成功')
    loadList()
  } catch (e) {
    toast.error(e.message)
  }
}

async function handleRefund(row) {
  if (!window.confirm(`确认对订单 ${row.orderNo} 执行退款？\n金额 ￥${row.totalAmount} 将原路退回买家。`)) return
  const reason = window.prompt('请输入退款原因（必填，会记录到操作日志）', '')
  if (reason === null) return
  if (!reason.trim()) {
    toast.error('请填写退款原因')
    return
  }
  try {
    await adminOrderApi.refund(row.id, reason.trim())
    toast.success('退款成功')
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
        placeholder="订单号 / 买家ID"
        @keyup.enter="handleSearch"
      />
      <select v-model="query.status" class="select">
        <option :value="null">全部状态</option>
        <option v-for="s in statusOptions" :key="s.value" :value="s.value">
          {{ s.label }}
        </option>
      </select>
      <input v-model="query.startTime" class="input" type="date" />
      <span class="range-sep">至</span>
      <input v-model="query.endTime" class="input" type="date" />
      <button class="btn btn-primary" @click="handleSearch">查询</button>
      <button class="btn" @click="handleReset">重置</button>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>订单号</th>
          <th>买家</th>
          <th>金额</th>
          <th>状态</th>
          <th>收货人</th>
          <th>下单时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in list" :key="row.id">
          <td>{{ row.orderNo }}</td>
          <td>{{ row.buyerId }}</td>
          <td>￥{{ row.totalAmount }}</td>
          <td>{{ statusMap[row.status] || row.status }}</td>
          <td>{{ row.receiverName || '—' }}</td>
          <td>{{ row.createTime || '—' }}</td>
          <td>
            <button v-if="canShip(row)" class="btn-link btn-link-gap" @click="handleShip(row)">
              发货
            </button>
            <button
              v-if="canCancel(row)"
              class="btn-link btn-link-danger"
              @click="handleCancel(row)"
            >
              强制关单
            </button>
            <button v-if="canRefund(row)" class="btn-link" @click="handleRefund(row)">退款</button>
            <span v-if="!canShip(row) && !canCancel(row) && !canRefund(row)" class="no-action">—</span>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="!list.length && !loading" class="empty">暂无订单数据</p>

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
.range-sep {
  color: var(--c-text-sub);
  font-size: 13px;
}
.btn-link-danger {
  color: var(--c-danger);
  margin-right: 12px;
}
/* 多个操作按钮并排时的间距（最后一个按钮不需要，故不加在 .btn-link 上） */
.btn-link-gap {
  margin-right: 12px;
}
.no-action {
  color: var(--c-text-sub);
}
</style>
