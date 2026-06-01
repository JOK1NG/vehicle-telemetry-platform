import { ref } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'

/** 动态加载高德地图 JS API */
export function useAMap() {
  const amapReady = ref(false)
  /** AMap 全局对象 */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let AMapInstance: any = null
  /** AMapLoader 加载错误信息 */
  const amapLoadError = ref<string | null>(null)
  const loading = ref(false)

  async function load(): Promise<void> {
    if (amapReady.value || AMapInstance) return
    loading.value = true
    amapLoadError.value = null

    const key = import.meta.env.VITE_AMAP_KEY
    if (!key) {
      amapLoadError.value = '请在 .env.local 中配置 VITE_AMAP_KEY（高德 JS API Key）'
      loading.value = false
      return
    }

    const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_JS_CODE
    if (securityJsCode) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      ;(window as any)._AMapSecurityConfig = {
        securityJsCode,
      }
    }

    try {
      AMapInstance = await AMapLoader.load({
        key,
        version: '2.0',
      })
      amapReady.value = true
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '未知错误'
      amapLoadError.value = `高德地图加载失败：${msg}`
    } finally {
      loading.value = false
    }
  }

  /** 创建一个地图实例到指定 DOM 容器 */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  function createMap(containerId: string, options?: { zoom?: number; center?: [number, number] }): any {
    if (!AMapInstance) return null

    return new AMapInstance.Map(containerId, {
      zoom: options?.zoom ?? 13,
      center: options?.center ?? [121.473701, 31.230416],
      viewMode: '2D',
    })
  }

  function getAMap() {
    return AMapInstance
  }

  return { loading, load, amapReady, amapLoadError, createMap, getAMap }
}
