
const KEY = import.meta.env.VITE_AMAP_KEY
const SECURITY_CODE = import.meta.env.VITE_AMAP_SECURITY_CODE
const SDK_URL = 'https://webapi.amap.com/maps?v=2.0&key='

/** 是否缺少 Key（供 UI 做友好降级提示，而不是白屏） */
export const isAMapKeyMissing = !KEY

/** 缓存的加载 Promise：同一时刻只加载一份 SDK */
let loadingPromise = null

export function loadAMap() {
  // 已加载完成
  if (window.AMap) return Promise.resolve(window.AMap)

  // 正在加载中：复用同一个 Promise，避免并发重复注入
  if (loadingPromise) return loadingPromise

  if (!KEY) {
    return Promise.reject(
      new Error('未配置高德 Key，请在 web-ui/.env.local 中填写 VITE_AMAP_KEY 后重启 dev server')
    )
  }

  // 关键顺序：安全密钥必须先于 SDK 脚本设置
  window._AMapSecurityConfig = { securityJsCode: SECURITY_CODE }

  loadingPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `${SDK_URL}${KEY}`
    script.async = true

    script.onload = () => {
      if (window.AMap) resolve(window.AMap)
      else reject(new Error('高德 SDK 已加载，但未挂载 window.AMap'))
    }
    script.onerror = () => {
      // 失败必须清空缓存，否则后续重试会一直拿到这个失败的 Promise
      loadingPromise = null
      reject(new Error('高德 SDK 加载失败，请检查网络或 Key 是否正确'))
    }

    document.head.appendChild(script)
  })

  return loadingPromise
}

function loadGeocoderPlugin(AMap) {
  if (AMap.Geocoder) return Promise.resolve()
  if (typeof AMap.plugin !== 'function') {
    return Promise.reject(new Error('当前高德 SDK 不支持按需加载插件'))
  }
  return new Promise((resolve, reject) => {
    AMap.plugin('AMap.Geocoder', () => {
      AMap.Geocoder ? resolve() : reject(new Error('高德地理编码插件加载失败'))
    })
  })
}

export async function geocode(address) {
  const AMap = await loadAMap()
  await loadGeocoderPlugin(AMap)

  const geocoder = new AMap.Geocoder()

  return new Promise((resolve, reject) => {
    geocoder.getLocation(address, (status, result) => {
      if (status !== 'complete' || !result?.geocodes?.length) {
        reject(new Error(`地址解析失败：${address}`))
        return
      }

      const geo = result.geocodes[0]
      // JS API 2.0 返回 AMap.LngLat 对象；1.x 等版本返回 "lng,lat" 字符串，两种都兼容
      const raw = geo.location
      const lng = typeof raw?.getLng === 'function' ? raw.getLng() : Number(String(raw).split(',')[0])
      const lat = typeof raw?.getLat === 'function' ? raw.getLat() : Number(String(raw).split(',')[1])

      // 校验必须做：解析"成功"但坐标是 NaN 的情况真实存在（地址过于模糊）
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
        reject(new Error(`地址解析结果异常：${address}`))
        return
      }

      resolve({ lng, lat, formatted: geo.formattedAddress || address })
    })
  })
}
