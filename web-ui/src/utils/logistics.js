/**
 * 物流「模拟」数据层
 *
 * 设计约束（很重要，别把这里做成随机数）：
 * 1) 收货地必须来自订单的真实地址（receiverAddress）。
 *    绝不能"按订单号哈希随便挑一个城市" —— 那样地图上的收货地和订单上写的地址对不上，
 *    用户一眼就看出是假的。文本地址 → 坐标，唯一正规做法是「地理编码」。
 * 2) 坐标统一使用 GCJ-02（火星坐标系）—— 与高德底图同源，绝不能混入 GPS 的 WGS-84。
 *    好在地理编码返回的就是 GCJ-02，同源，不用做坐标转换。
 * 3) 地理编码是网络请求，会失败（配额、网络、地址过于模糊）。失败必须「显式降级」，
 *    并把"这是近似位置"如实告诉用户，而不是悄悄换一个城市假装成功。
 * 4) 这里只做「展示用」模拟。将来接真实物流时，只需把 resolveRoute / buildTimeline
 *    换成后端返回的运单数据，组件层完全不用动。
 */

import { geocode } from './amap'

/** 平台发货仓（GCJ-02） */
export const PLATFORM_WAREHOUSE = {
  name: '巧见 · 杭州手作仓',
  lng: 120.15507,
  lat: 30.274084
}

const FALLBACK_CITIES = [
  { key: '贵阳', name: '贵州省 · 贵阳市', lng: 106.630153, lat: 26.647661 },
  { key: '上海', name: '上海市 · 黄浦区', lng: 121.472644, lat: 31.231706 },
  { key: '北京', name: '北京市 · 东城区', lng: 116.397499, lat: 39.908722 },
  { key: '广州', name: '广州市 · 越秀区', lng: 113.264434, lat: 23.129162 },
  { key: '深圳', name: '深圳市 · 福田区', lng: 114.057868, lat: 22.543099 },
  { key: '成都', name: '成都市 · 锦江区', lng: 104.066541, lat: 30.572269 },
  { key: '西安', name: '西安市 · 碑林区', lng: 108.948024, lat: 34.263161 },
  { key: '武汉', name: '武汉市 · 武昌区', lng: 114.298572, lat: 30.584355 },
  { key: '南京', name: '南京市 · 玄武区', lng: 118.767413, lat: 32.041544 },
  { key: '长沙', name: '长沙市 · 岳麓区', lng: 112.982279, lat: 28.19409 },
  { key: '青岛', name: '青岛市 · 市南区', lng: 120.383122, lat: 36.066229 }
]

/** 确定性字符串哈希 → 同一输入永远得到同一结果 */
function hashOf(input) {
  const s = String(input ?? '')
  let h = 0
  for (let i = 0; i < s.length; i++) {
    h = (h * 31 + s.charCodeAt(i)) >>> 0
  }
  return h
}

/** 在兜底表里按城市名匹配（地址里含"贵阳"就命中贵阳） */
function matchFallbackCity(address) {
  return FALLBACK_CITIES.find((c) => address.includes(c.key)) ?? null
}

export async function resolveRoute(order) {
  const address = String(order?.receiverAddress ?? '').trim()

  if (address) {
    try {
      const { lng, lat } = await geocode(address)
      return {
        origin: PLATFORM_WAREHOUSE,
        dest: { name: `收货地 · ${address}`, lng, lat },
        note: ''
      }
    } catch (e) {
      // 降级是有意为之：宁可显示"近似位置"，也不要让整块地图画不出来
      const city = matchFallbackCity(address)
      if (city) {
        return {
          origin: PLATFORM_WAREHOUSE,
          dest: { name: `收货地 · ${city.name}`, lng: city.lng, lat: city.lat },
          note: `精确地址定位失败，已按「${city.name}」近似展示`
        }
      }
    }
  }

  const fallback = FALLBACK_CITIES[hashOf(order?.id ?? order?.orderNo) % FALLBACK_CITIES.length]
  return {
    origin: PLATFORM_WAREHOUSE,
    dest: { name: `收货地 · ${fallback.name}`, lng: fallback.lng, lat: fallback.lat },
    note: '收货地址无法定位，当前展示的是示意路线'
  }
}

/** 球面距离（公里）—— 不依赖任何地图 SDK，纯数学，方便单测 */
export function haversineKm(a, b) {
  const R = 6371
  const toRad = (d) => (d * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(s)))
}

/**
 * 兼容后端时间格式：可能是 ISO 字符串，也可能是 [y,m,d,h,mi,s] 数组
 * （Java LocalDateTime 被某些序列化配置输出成数组，这是常见坑）
 */
export function parseTime(t) {
  if (!t) return null
  if (Array.isArray(t)) {
    const [y, m = 1, d = 1, h = 0, mi = 0, s = 0] = t
    return new Date(y, m - 1, d, h, mi, s).getTime()
  }
  const ms = new Date(String(t).replace(' ', 'T')).getTime()
  return Number.isNaN(ms) ? null : ms
}

export function formatTime(t, withYear = false) {
  const ms = typeof t === 'number' ? t : parseTime(t)
  if (!ms) return '—'
  const d = new Date(ms)
  const p = (n) => String(n).padStart(2, '0')
  const md = `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
  return withYear ? `${d.getFullYear()}-${md}` : md
}

/** 一单运输的模拟总时长（小时）：和当前订单状态配合，形成"进度感" */
const TRANSIT_HOURS = 24

export function calcProgress(order) {
  if (!order) return 0
  if (order.status === 'RECEIVED' || order.status === 'COMPLETED') return 1
  if (order.status !== 'SHIPPED') return 0

  const shippedAt = parseTime(order.updateTime) ?? parseTime(order.createTime)
  if (!shippedAt) return 0.5
  const hours = (Date.now() - shippedAt) / 36e5
  return Math.min(0.95, Math.max(0.05, hours / TRANSIT_HOURS))
}

/** 轨迹节点模板：at 是进度阈值，进度越过谁，谁就被点亮 */
const NODE_TEMPLATE = [
  { at: 0, title: '平台已发货', desc: '包裹已从仓库出库，等待揽收' },
  { at: 0.12, title: '快递已揽收', desc: '杭州转运中心已收件' },
  { at: 0.35, title: '运输中', desc: '包裹已离开杭州，发往目的地' },
  { at: 0.68, title: '到达目的地城市', desc: '' },
  { at: 0.88, title: '派送中', desc: '快递员正在派送，请保持电话畅通' },
  { at: 1, title: '已签收', desc: '包裹已送达，感谢你的等待' }
]

/**
 * 生成轨迹时间线：done 表示该节点是否已发生
 * 时间用「发货时间 + 进度比例 × 总时长」反推，保证时间与进度自洽
 */
export function buildTimeline(order, route, progress) {
  const base =
    parseTime(order?.updateTime) ?? parseTime(order?.createTime) ?? Date.now()
  return NODE_TEMPLATE.map((n) => ({
    title: n.title,
    desc: n.desc || route.dest.name,
    time: formatTime(base + n.at * TRANSIT_HOURS * 36e5, true),
    done: progress >= n.at
  }))
}
