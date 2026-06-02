import { useCallback, useEffect, useRef, useState } from 'react';
import AMapLoader from '@amap/amap-jsapi-loader';

type AMapInstance = unknown;
type AMapMap = unknown;

export function useAMap() {
  const [amapReady, setAmapReady] = useState(false);
  const [amapLoadError, setAmapLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const AMapRef = useRef<AMapInstance | null>(null);

  const load = useCallback(async (): Promise<void> => {
    if (amapReady || AMapRef.current) return;
    setLoading(true);
    setAmapLoadError(null);

    const key = import.meta.env.VITE_AMAP_KEY;
    if (!key) {
      setAmapLoadError('请在 .env.local 中配置 VITE_AMAP_KEY（高德 JS API Key）');
      setLoading(false);
      return;
    }

    const securityJsCode = import.meta.env.VITE_AMAP_SECURITY_JS_CODE;
    if (securityJsCode) {
      (window as unknown as { _AMapSecurityConfig: { securityJsCode: string } })._AMapSecurityConfig = {
        securityJsCode,
      };
    }

    try {
      AMapRef.current = await AMapLoader.load({ key, version: '2.0' });
      setAmapReady(true);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '未知错误';
      setAmapLoadError(`高德地图加载失败：${msg}`);
    } finally {
      setLoading(false);
    }
  }, [amapReady]);

  const createMap = useCallback(
    (container: HTMLElement, options?: { zoom?: number; center?: [number, number] }): AMapMap | null => {
      if (!AMapRef.current) return null;
      const AMap = AMapRef.current as { Map: new (container: HTMLElement, opts: object) => AMapMap };
      return new AMap.Map(container, {
        zoom: options?.zoom ?? 13,
        center: options?.center ?? [121.473701, 31.230416],
        viewMode: '2D',
      });
    },
    []
  );

  useEffect(() => {
    return () => {
      AMapRef.current = null;
    };
  }, []);

  return { loading, load, amapReady, amapLoadError, createMap, getAMap: () => AMapRef.current };
}
