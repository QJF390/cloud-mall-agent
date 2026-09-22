<script setup>
/**
 * 物流跟踪（业务编排组件）
 *
 * 它负责：把订单翻译成"路线 + 进度 + 轨迹节点"，再交给 LogisticsMap 去画。
 * 数据全部来自 logistics.js 的模拟层 —— 将来换成真实运单接口时，只改那一个文件。
 */
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import LogisticsMap from './LogisticsMap.vue'
import {
  resolveRoute,
  buildTimeline,
  calcProgress,
  haversineKm
} from '@/utils/logistics'

const props = defineProps({
  order: { type: Object, required: true }
})
const emit = defineEmits(['close'])

const route = ref(null)
const locating = ref(true)
const locateNote = ref('')

async function loadRoute() {
  locating.value = true
  route.value = null
  simProgress.value = null
  try {
    const resolved = await resolveRoute(props.order)
    route.value = resolved
    locateNote.value = resolved.note || ''
  } catch (e) {
    // resolveRoute 内部已经把能降级的都降级了，走到这里属于意外错误
    locateNote.value = e?.message || '物流路线生成失败'
  } finally {
    locating.value = false
  }
}

/** 由订单状态推算的真实进度（SHIPPED 会随时间缓慢推进，但不自行到 1） */
const realProgress = computed(() => calcProgress(props.order))

/** 模拟进度：为 null 时使用真实进度 */
const simProgress = ref(null)
const progress = computed(() =>
  simProgress.value == null ? realProgress.value : simProgress.value
)

/**
 * 必须放在 simProgress 声明「之后」：
 * immediate 的 watch 会立刻执行 loadRoute，而 loadRoute 里要写 simProgress，
 * 提前触发会命中 const 的暂时性死区（TDZ）直接报错。
 */
watch(() => props.order?.id, loadRoute, { immediate: true })

/* ---------- 模拟运输 ---------- */
const simulating = ref(false)
let timer = null

function stopSim() {
  if (timer) clearInterval(timer)
  timer = null
  simulating.value = false
}

function startSim() {
  stopSim()
  simulating.value = true
  simProgress.value = 0 // 从发货仓重新出发，动画更直观
  timer = setInterval(() => {
    const next = Math.min(1, (simProgress.value ?? 0) + 0.02)
    simProgress.value = next
    if (next >= 1) stopSim() // 到站即停，不做"来回穿梭"的假动画
  }, 160)
}

const simLabel = computed(() => {
  if (simulating.value) return '运输中…'
  return simProgress.value == null ? '模拟运输' : '重新模拟'
})

/* ---------- 派生展示数据 ---------- */
const totalKm = computed(() =>
  route.value ? haversineKm(route.value.origin, route.value.dest) : 0
)
const percent = computed(() => Math.round(progress.value * 100))
const passedKm = computed(() => totalKm.value * progress.value)
const remainKm = computed(() => Math.max(0, totalKm.value - passedKm.value))
// route 未就绪时给空数组，避免 buildTimeline 里读 route.dest 报错
const timeline = computed(() =>
  route.value ? buildTimeline(props.order, route.value, progress.value) : []
)

/** 模拟运单号：真实项目里这个值来自平台发货时录入的快递单号 */
const trackingNo = computed(() => {
  const digits = String(props.order?.orderNo ?? props.order?.id ?? '').replace(/\D/g, '')
  return 'QT' + (digits.slice(-12) || '000000000000')
})

/* ---------- 关闭交互 ---------- */
function onKeydown(e) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  stopSim()
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="track">
    <!-- 头部 -->
    <header class="track-head">
      <div class="head-main">
        <h3 class="track-title">物流跟踪</h3>
        <p class="track-sub">
          运单号 {{ trackingNo }}
          <template v-if="route"> · {{ route.origin.name }} → {{ route.dest.name }}</template>
        </p>
      </div>
      <button class="track-close" type="button" aria-label="关闭" @click="emit('close')">×</button>
    </header>

    <div class="track-body">
      <!-- 左：地图 + 进度浮层 -->
      <div class="track-map">
        <!-- 坐标是异步解析出来的：拿到之前不要挂地图。
             key 用终点坐标：不同订单/不同终点时强制重建，避免地图复用旧的点 -->
        <LogisticsMap
          v-if="route"
          :key="`${route.dest.lng},${route.dest.lat}`"
          :origin="route.origin"
          :dest="route.dest"
          :progress="progress"
          height="100%"
        />
        <div v-else class="map-pending">
          <span class="map-pending-spinner"></span>
          <span>正在按收货地址定位…</span>
        </div>

        <div v-if="route" class="hud">
          <div class="hud-bar">
            <span class="hud-fill" :style="{ width: percent + '%' }"></span>
          </div>
          <div class="hud-row">
            <span>已运输 <b>{{ percent }}%</b></span>
            <span>剩余约 <b>{{ remainKm.toFixed(0) }}</b> km</span>
            <span class="hud-total">全程 {{ totalKm.toFixed(0) }} km</span>
          </div>
        </div>
      </div>

      <!-- 右：轨迹时间线 -->
      <div class="track-side">
        <div class="sim-bar">
          <button
            class="btn btn-ghost btn-xs"
            type="button"
            :disabled="simulating"
            @click="startSim"
          >
            {{ simLabel }}
          </button>
          <span class="sim-tip">当前为模拟数据</span>
        </div>

        <ol class="nodes">
          <li v-for="n in timeline" :key="n.title" :class="['node', { done: n.done }]">
            <span class="node-dot"></span>
            <div class="node-main">
              <p class="node-title">{{ n.title }}</p>
              <p class="node-desc">{{ n.desc }}</p>
              <p class="node-time">{{ n.time }}</p>
            </div>
          </li>
        </ol>

        <p v-if="locating" class="track-tip">正在解析收货地址…</p>
        <p v-else-if="locateNote" class="track-tip track-tip--warn">{{ locateNote }}</p>

        <p class="track-note">
          以上为轨迹示意图。接入真实物流后，此列表将替换为快递公司返回的节点。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.track {
  display: flex;
  flex-direction: column;
  /* 必须是确定高度而非 max-height：地图容器高度是 100%，
     在"高度不确定"的父级里 100% 会塌成 0，地图直接不可见 */
  height: min(84vh, 720px);
  overflow: hidden;
}

/* ---------- 头部 ---------- */
.track-head {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px 16px;
  border-bottom: 1px solid var(--c-line);
}
.track-title {
  font-family: var(--font-serif);
  font-size: 19px;
  font-weight: 600;
}
.track-sub {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--c-text-mute);
  word-break: break-all;
}
.track-close {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  font-size: 20px;
  line-height: 1;
  color: var(--c-text-mute);
  background: var(--c-bg-deep);
  border-radius: var(--r-pill);
  transition: all 0.25s var(--ease);
}
.track-close:hover {
  color: var(--c-text);
  background: var(--c-line);
}

/* ---------- 主体：左图右线 ---------- */
.track-body {
  flex: 1 1 auto;
  display: flex;
  gap: 18px;
  padding: 18px 22px 22px;
  min-height: 0;
}
.track-map {
  position: relative;
  flex: 1 1 58%;
  min-width: 0;
  min-height: 380px;
}
.track-side {
  flex: 0 0 40%;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

/* ---------- 进度浮层 ---------- */
.hud {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  /* 高德内部的标点/控件层自带 z-index（100 级），
     浮层想盖在地图上就必须高于它，否则会被地图挡在后面 */
  z-index: 400;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.92);
  border-radius: var(--r-md);
  box-shadow: var(--sh-card);
  backdrop-filter: blur(4px);
}
.hud-bar {
  height: 4px;
  background: var(--c-line);
  border-radius: var(--r-pill);
  overflow: hidden;
}
.hud-fill {
  display: block;
  height: 100%;
  background: var(--c-accent);
  border-radius: var(--r-pill);
  transition: width 0.3s var(--ease);
}
.hud-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--c-text-sub);
}
.hud-row b {
  color: var(--c-accent-deep);
}
.hud-total {
  color: var(--c-text-mute);
}

/* ---------- 定位中的占位（此时还没有坐标，不能挂地图） ---------- */
.map-pending {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 13.5px;
  color: var(--c-text-sub);
  background: var(--c-bg-deep);
  border-radius: var(--r-md);
}
.map-pending-spinner {
  width: 22px;
  height: 22px;
  border: 2px solid var(--c-line);
  border-top-color: var(--c-accent);
  border-radius: 50%;
  animation: track-spin 0.8s linear infinite;
}
@keyframes track-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ---------- 模拟栏 ---------- */
.sim-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.btn-xs {
  height: 30px;
  padding: 0 14px;
  font-size: 12.5px;
}
.sim-tip {
  font-size: 12px;
  color: var(--c-text-mute);
}

/* ---------- 时间线 ---------- */
.nodes {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}
.node {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 0 0 18px 6px;
}
.node:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 10px;
  top: 14px;
  bottom: 0;
  width: 1px;
  background: var(--c-line);
}
.node-dot {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--c-line);
  box-shadow: 0 0 0 3px var(--c-surface);
}
.node.done .node-dot {
  background: var(--c-accent);
}
.node-main {
  min-width: 0;
}
.node-title {
  font-size: 13.5px;
  color: var(--c-text-mute);
}
.node.done .node-title {
  color: var(--c-text);
  font-weight: 600;
}
.node-desc {
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--c-text-mute);
}
.node-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--c-text-mute);
  opacity: 0.85;
}
.track-tip {
  flex-shrink: 0;
  margin-top: 12px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--c-text-sub);
}
/* 降级提示必须是"警告色"而不是普通说明文字：
   用户有权知道地图上这个点是精确地址还是近似位置 */
.track-tip--warn {
  color: var(--c-danger);
}

.track-note {
  flex-shrink: 0;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--c-line);
  font-size: 12px;
  line-height: 1.7;
  color: var(--c-text-mute);
}

@media (max-width: 760px) {
  .track-body {
    flex-direction: column;
  }
  .track-map {
    flex: 0 0 260px;
    min-height: 260px;
  }
  .track-side {
    flex: 1 1 auto;
    min-height: 0;
  }
}
</style>
