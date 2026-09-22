<script setup>

import { ref, shallowRef, watch, onMounted, onUnmounted } from 'vue'
import { loadAMap } from '@/utils/amap'

const props = defineProps({
  origin: { type: Object, required: true }, // { name, lng, lat }
  dest: { type: Object, required: true }, // { name, lng, lat }
  progress: { type: Number, default: 0 }, // 0~1
  height: { type: String, default: '380px' }
})

const el = ref(null)
const status = ref('loading') // loading | ready | error
const errMsg = ref('')

// 用 shallowRef 保存 AMap 原生实例：
// 这些对象内部结构极其庞大，若被 Vue 深度代理，地图渲染会明显掉帧。
// 地图/图表类集成，一律 shallowRef 或 markRaw。
const map = shallowRef(null)
const truck = shallowRef(null)
const passedLine = shallowRef(null)

let display = 0 // 当前"显示"的进度，用于平滑动画
let raf = null
let ro = null

const clamp01 = (n) => Math.min(1, Math.max(0, Number(n) || 0))
const lerp = (a, b, t) => a + (b - a) * t

/** 按进度取直线上的点 */
function pointAt(p) {
  return [
    lerp(props.origin.lng, props.dest.lng, p),
    lerp(props.origin.lat, props.dest.lat, p)
  ]
}

/** 端点圆点（内联样式，因为高德 Marker 的 content 是运行时注入的 DOM，scoped 不会生效） */
function dotHTML(color) {
  return `<span style="display:block;width:12px;height:12px;border-radius:50%;
    background:${color};border:2px solid #fff;box-shadow:0 1px 6px rgba(51,51,51,.3)"></span>`
}

function applyProgress(p) {
  const pos = pointAt(p)
  truck.value?.setPosition(pos)
  passedLine.value?.setPath([[props.origin.lng, props.origin.lat], pos])
}

/**
 * 指数逼近动画：每帧把与目标值的差距缩小 7%。
 * 比"固定步长"好在——起步快、临近终点自然减速，且永远不会在终点来回抖动。
 */
function tick() {
  const target = clamp01(props.progress)
  const diff = target - display

  if (Math.abs(diff) < 0.001) {
    display = target
    applyProgress(display)
    raf = null
    return
  }

  display += diff * 0.07
  applyProgress(display)
  raf = requestAnimationFrame(tick)
}

function startAnim() {
  if (raf == null && map.value) raf = requestAnimationFrame(tick)
}

watch(() => props.progress, startAnim)

async function init() {
  try {
    const AMap = await loadAMap()
    if (!el.value) return // 异步返回时组件可能已卸载，必须防一手

    map.value = new AMap.Map(el.value, {
      zoom: 6,
      viewMode: '2D',
      // 素色底图，和项目米白主色调保持一致，不让地图抢视觉
      mapStyle: 'amap://styles/whitesmoke'
    })

    const start = [props.origin.lng, props.origin.lat]
    const end = [props.dest.lng, props.dest.lat]

    // 底层虚线：完整路线
    const baseLine = new AMap.Polyline({
      path: [start, end],
      strokeColor: '#d9d4cc',
      strokeWeight: 3,
      strokeStyle: 'dashed',
      strokeDasharray: [6, 6],
      lineJoin: 'round'
    })

    // 上层实线：已走过的部分（随进度增长）
    passedLine.value = new AMap.Polyline({
      path: [start, start],
      strokeColor: '#3e8fb0',
      strokeWeight: 4,
      lineJoin: 'round',
      lineCap: 'round'
    })

    const originMarker = new AMap.Marker({
      position: start,
      anchor: 'center',
      content: dotHTML('#5b9a7a'),
      zIndex: 90
    })
    const destMarker = new AMap.Marker({
      position: end,
      anchor: 'center',
      content: dotHTML('#4a7fb5'),
      zIndex: 90
    })

    truck.value = new AMap.Marker({
      position: start,
      anchor: 'center',
      zIndex: 120,
      content:
        '<span class="lj-truck"><span class="lj-truck-ring"></span><span class="lj-truck-core"></span></span>'
    })

    map.value.add([baseLine, passedLine.value, originMarker, destMarker, truck.value])
    // 自动缩放到"刚好装下两个点"，四边各留 70px 内边距
    map.value.setFitView([originMarker, destMarker], false, [70, 70, 70, 70])

    status.value = 'ready'
    applyProgress(0)
    startAnim() // 打开时从发货仓滑到当前进度，本身就是一段进场动画

    // 模态框展开动画、窗口缩放都会改变容器尺寸。
    // 容器变了地图不 resize，就会灰屏/错位 —— 这是地图组件最常见的线上 bug。
    ro = new ResizeObserver(() => map.value?.resize())
    ro.observe(el.value)
  } catch (e) {
    status.value = 'error'
    errMsg.value = e?.message || '地图加载失败'
  }
}

onUnmounted(() => {
  if (raf) cancelAnimationFrame(raf)
  raf = null
  ro?.disconnect()
  ro = null

  map.value?.destroy()
  map.value = null
})

onMounted(init)
</script>

<template>
  <div class="lj-map" :style="{ height }">
    <div ref="el" class="lj-map-canvas"></div>

    <div v-if="status === 'loading'" class="lj-map-hint">
      <span class="lj-map-spinner"></span>
      <span>正在加载地图…</span>
    </div>

    <div v-else-if="status === 'error'" class="lj-map-hint lj-map-hint--error">
      <p class="lj-map-hint-title">地图暂时画不出来</p>
      <p class="lj-map-hint-desc">{{ errMsg }}</p>
    </div>
  </div>
</template>

<style scoped>
.lj-map {
  position: relative;
  width: 100%;
  background: var(--c-bg-deep);
  border-radius: var(--r-md);
  overflow: hidden;
}
.lj-map-canvas {
  width: 100%;
  height: 100%;
}

.lj-map-hint {
  position: absolute;
  inset: 0;
  z-index: 400;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 13.5px;
  color: var(--c-text-sub);
  background: var(--c-bg-deep);
  text-align: center;
  padding: 20px;
}
.lj-map-hint--error {
  color: var(--c-danger);
}
.lj-map-hint-title {
  font-size: 15px;
  color: var(--c-text);
}
.lj-map-hint-desc {
  font-size: 12.5px;
  color: var(--c-text-mute);
  max-width: 320px;
  line-height: 1.7;
}
.lj-map-spinner {
  width: 22px;
  height: 22px;
  border: 2px solid var(--c-line);
  border-top-color: var(--c-accent);
  border-radius: 50%;
  animation: lj-spin 0.8s linear infinite;
}
@keyframes lj-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

<style>
.lj-truck {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
}
.lj-truck-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(62, 143, 176, 0.4);
  animation: lj-pulse 1.6s ease-out infinite;
}
.lj-truck-core {
  position: relative;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #3e8fb0;
  border: 2px solid #fff;
  box-shadow: 0 1px 6px rgba(47, 115, 145, 0.55);
}
@keyframes lj-pulse {
  0% {
    transform: scale(0.5);
    opacity: 0.9;
  }
  70% {
    transform: scale(1);
    opacity: 0;
  }
  100% {
    transform: scale(1);
    opacity: 0;
  }
}
</style>
