<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { adminDashboardApi } from '@/api/modules'
import { toast } from '@/utils/toast'

/** 控制台首页：聚合用户 / 商品 / 订单概览 + 销量成交趋势图 */
const loading = ref(false)
const data = ref({})
const degraded = ref(false)

const stats = computed(() => [
  { label: '用户总数', value: data.value.userCount ?? '--' },
  { label: '商品总数', value: data.value.productCount ?? '--' },
  { label: '订单总数', value: data.value.orderCount ?? '--' },
  { label: '今日成交额', value: `￥${data.value.todayAmount ?? 0}` }
])

async function loadStats() {
  loading.value = true
  try {
    const res = await adminDashboardApi.stats()
    data.value = res.data || {}
    degraded.value = Boolean(res.data?.degraded)
    if (degraded.value) {
      toast.error('部分下游服务不可用，统计数字可能不准确')
    }
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

/** ====== 销量 / 成交额趋势图 ====== */
const trendDays = ref(7)
const trendLoading = ref(false)
const chartEl = ref(null)
// 实例不放 ref：避免被 Vue 响应式代理拖慢 echarts 内部更新
let chart = null

async function loadTrend() {
  trendLoading.value = true
  try {
    const res = await adminDashboardApi.trend(trendDays.value)
    renderTrend(res.data || {})
  } catch (e) {
    toast.error(e.message)
  } finally {
    trendLoading.value = false
  }
}

function renderTrend(trend) {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['销量（单）', '成交额（元）'] },
    grid: { left: 48, right: 56, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: trend.dates ?? [] },
    yAxis: [
      { type: 'value', name: '销量', minInterval: 1 },
      { type: 'value', name: '成交额' }
    ],
    series: [
      { name: '销量（单）', type: 'bar', data: trend.orderCount ?? [], barMaxWidth: 32 },
      { name: '成交额（元）', type: 'line', yAxisIndex: 1, smooth: true, data: trend.amount ?? [] }
    ]
  })
}

function handleResize() {
  chart?.resize()
}

function switchTrendDays(days) {
  if (trendDays.value === days) return
  trendDays.value = days
  loadTrend()
}

onMounted(() => {
  loadStats()
  loadTrend()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  // dispose： echarts 实例持有 DOM 和定时器，不释放会内存泄漏（SPA 路由切换高频触发）
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="dashboard">
    <div class="stat-grid">
      <div v-for="s in stats" :key="s.label" class="card stat-card">
        <p class="stat-label">{{ s.label }}</p>
        <p class="stat-value">{{ s.value }}</p>
      </div>
    </div>

    <div class="card dashboard-panel">
      <div class="panel-head">
        <h2 class="panel-title">销量 / 成交额趋势</h2>
        <div class="trend-actions">
          <div class="seg">
            <button
              v-for="d in [7, 14, 30]"
              :key="d"
              class="seg-btn"
              :class="{ active: trendDays === d }"
              :disabled="trendLoading"
              @click="switchTrendDays(d)"
            >
              {{ d }}天
            </button>
          </div>
          <button class="btn" :disabled="trendLoading" @click="loadTrend">
            {{ trendLoading ? '加载中…' : '刷新' }}
          </button>
        </div>
      </div>

      <p v-if="degraded" class="warn">⚠️ 订单服务不可用，趋势数据为降级结果。</p>

      <!-- 固定高度：echarts 需要确定尺寸的容器，不设高度会渲染成 0 -->
      <div ref="chartEl" class="trend-chart"></div>
    </div>

    <div class="card dashboard-panel">
      <div class="panel-head">
        <h2 class="panel-title">待办概览</h2>
        <button class="btn" :disabled="loading" @click="loadStats">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
      </div>

      <p v-if="degraded" class="warn">⚠️ 有下游服务不可用，以下数字为降级结果。</p>

      <ul class="todo-list">
        <li>
          <span>待处理订单（待付款 / 已付款未发货）</span>
          <strong>{{ data.pendingOrderCount ?? '--' }}</strong>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}
.stat-label {
  font-size: 13px;
  color: var(--c-text-sub);
}
.stat-value {
  margin-top: 10px;
  font-size: 26px;
  font-weight: 600;
}
.dashboard-panel {
  margin-top: 16px;
}
.trend-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.seg {
  display: flex;
  gap: 4px;
}
.seg-btn {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  background: transparent;
  color: var(--c-text-sub);
  cursor: pointer;
}
.seg-btn.active {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.trend-chart {
  height: 320px;
  margin-top: 14px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.panel-title {
  font-size: 15px;
}
.warn {
  margin-top: 12px;
  font-size: 13px;
  color: var(--c-warning);
}
.todo-list {
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}
.todo-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--c-border);
  font-size: 14px;
}
.todo-list li:last-child {
  border-bottom: none;
}
.todo-list strong {
  font-size: 18px;
  color: var(--c-primary);
}
</style>
