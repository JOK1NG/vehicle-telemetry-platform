import { useEffect, useRef, useState } from 'react';
import { useAMap } from '../../hooks/useAMap';
import { ZoomInIcon, ZoomOutIcon } from '../common/Icons';
import { cx } from '../common/utils';
import { createMarkerEl, updateMarkerEl, type MarkerData } from './vehicleMarker';

const SHANGHAI_CENTER: [number, number] = [121.473701, 31.230416];
const DEFAULT_ZOOM = 12;

type AMapMap = {
  setZoomAndCenter: (zoom: number, center: [number, number]) => void;
  setFitView: (overlays: unknown[], avoid?: boolean, padding?: number[], maxZoom?: number) => void;
  getZoom: () => number;
  getContainer: () => HTMLDivElement;
  destroy: () => void;
  on: (event: string, handler: (...args: unknown[]) => void) => void;
};

type AMapMarker = {
  setMap: (map: AMapMap | null) => void;
  setPosition: (pos: [number, number]) => void;
  setContent: (content: HTMLElement | string) => void;
  getContentDom?: () => HTMLElement;
  on: (event: string, handler: (...args: unknown[]) => void) => void;
  getExtData: () => unknown;
};

declare global {
  interface Window {
    AMap?: {
      Map: new (container: HTMLElement, opts: object) => AMapMap;
      Marker: new (opts: object) => AMapMarker;
    };
  }
}

export function TelemetryMap({
  realtime,
  selectedId,
  onSelect,
  onMapReady,
}: {
  realtime: MarkerData[];
  selectedId: number | null;
  onSelect: (id: number | null) => void;
  onMapReady?: (map: AMapMap) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<AMapMap | null>(null);
  const markersRef = useRef<Map<number, { marker: AMapMarker; el: HTMLDivElement }>>(new Map());
  const { loading, load, amapReady, amapLoadError, createMap, getAMap } = useAMap();

  const [zoomPercent, setZoomPercent] = useState(100);
  const [isReady, setIsReady] = useState(false);

  // Stabilize callback identities so the map init effect doesn't tear down
  // the AMap instance on every parent re-render (which fires on every WS tick).
  const onMapReadyRef = useRef(onMapReady);
  onMapReadyRef.current = onMapReady;
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;

  useEffect(() => {
    if (amapReady) return;
    load();
  }, [amapReady, load]);

  useEffect(() => {
    if (!amapReady || !containerRef.current || mapRef.current) return;
    const map = createMap(containerRef.current, { zoom: DEFAULT_ZOOM, center: SHANGHAI_CENTER }) as AMapMap | null;
    if (!map) return;
    mapRef.current = map;
    setIsReady(true);
    onMapReadyRef.current?.(map);

    const onZoomChange = () => {
      const z = map.getZoom();
      const pct = Math.round(((z - 3) / (20 - 3)) * 100 + 20);
      setZoomPercent(pct);
    };
    map.on('zoomchange', onZoomChange);
    onZoomChange();

    return () => {
      map.destroy();
      mapRef.current = null;
      markersRef.current.clear();
      setIsReady(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [amapReady, createMap]);

  useEffect(() => {
    if (!isReady || !getAMap() || !mapRef.current) return;
    const AMap = getAMap() as NonNullable<Window['AMap']>;

    realtime.forEach((v) => {
      const isSelected = selectedId === v.vehicleId;
      let entry = markersRef.current.get(v.vehicleId);

      if (!entry) {
        const el = createMarkerEl(v, isSelected);
        el.addEventListener('click', (e) => {
          e.stopPropagation();
          onSelectRef.current(v.vehicleId);
        });
        const marker = new AMap.Marker({
          position: [v.lng, v.lat],
          content: el,
          offset: { x: -18, y: -18 } as never,
          title: v.plateNo,
        });
        marker.setMap(mapRef.current);
        entry = { marker, el };
        markersRef.current.set(v.vehicleId, entry);
      } else {
        updateMarkerEl(entry.el, v, isSelected);
        entry.marker.setPosition([v.lng, v.lat]);
      }
    });

    markersRef.current.forEach((entry, id) => {
      if (!realtime.find((v) => v.vehicleId === id)) {
        entry.marker.setMap(null);
        markersRef.current.delete(id);
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [realtime, selectedId, isReady, getAMap]);

  const fitToVehicles = () => {
    if (!mapRef.current || realtime.length === 0) return;
    const markers = Array.from(markersRef.current.values()).map((e) => e.marker);
    if (markers.length === 0) return;
    mapRef.current.setFitView(markers, false, [72, 72, 72, 72], 16);
  };

  const resetView = () => {
    mapRef.current?.setZoomAndCenter(DEFAULT_ZOOM, SHANGHAI_CENTER);
    onSelect(null);
  };

  const zoomIn = () => {
    if (!mapRef.current) return;
    mapRef.current.setZoomAndCenter(mapRef.current.getZoom() + 1, SHANGHAI_CENTER);
  };
  const zoomOut = () => {
    if (!mapRef.current) return;
    const z = Math.max(3, mapRef.current.getZoom() - 1);
    mapRef.current.setZoomAndCenter(z, SHANGHAI_CENTER);
  };

  const focusVehicle = (id: number) => {
    const entry = markersRef.current.get(id);
    if (!entry || !mapRef.current) return;
    onSelect(id);
  };

  // Expose focusVehicle via ref or callback. For now, listen to selectedId.
  useEffect(() => {
    if (!mapRef.current || selectedId == null) return;
    const entry = markersRef.current.get(selectedId);
    if (!entry) return;
    // We can read the marker's position via getPosition if needed
    // For simplicity, just keep selection visible
  }, [selectedId]);

  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
      <div className="px-4 py-3 border-b border-[var(--border)] flex items-center justify-between gap-2 flex-wrap">
        <div>
          <div className="text-[13px] font-semibold">实时车辆位置</div>
          <div className="text-[11px] text-[var(--muted-foreground)]">
            订阅 /topic/vehicles · 坐标 GCJ-02 · 滚轮缩放 · 拖拽平移
          </div>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={fitToVehicles}
            disabled={realtime.length === 0}
            className="h-6 px-2 rounded text-[11px] font-medium border border-[var(--border)] hover:bg-[var(--muted)] disabled:opacity-40 inline-flex items-center gap-1"
            title="适配所有车辆视野"
          >
            <svg viewBox="0 0 24 24" fill="none" className="w-3 h-3">
              <path
                d="M4 9V5h4M20 9V5h-4M4 15v4h4M20 15v4h-4"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            适配
          </button>
          <button
            onClick={resetView}
            className="h-6 px-2 rounded text-[11px] font-medium border border-[var(--border)] hover:bg-[var(--muted)]"
            title="重置视图"
          >
            重置
          </button>
          <div className="flex items-center gap-1.5 ml-2 text-[11px] text-[var(--muted-foreground)]">
            <span className="w-1.5 h-1.5 rounded-full bg-[var(--chart-2)]" />
            在线
            <span className="w-1.5 h-1.5 rounded-full bg-[var(--muted-foreground)]/40 ml-2" />
            离线
          </div>
        </div>
      </div>

      <div className="relative aspect-[5/3]">
        <div
          ref={containerRef}
          className="w-full h-full"
          onClick={() => onSelect(null)}
        />
        {(loading || !isReady) && (
          <div className="absolute inset-0 grid place-items-center bg-[var(--card)]/60 text-[12px] text-[var(--muted-foreground)]">
            {amapLoadError || '地图加载中…'}
          </div>
        )}

        <div className="absolute bottom-3 left-3 flex items-center gap-2 px-2.5 py-1.5 rounded-md bg-white/85 backdrop-blur border border-[var(--border)] text-[10px] text-[var(--muted-foreground)]">
          <span className="font-mono">N</span>
          <span className="w-px h-3 bg-[var(--border)]" />
          <span className="num font-medium">{zoomPercent}%</span>
        </div>

        <div className="absolute bottom-3 right-3 flex flex-col gap-0.5 rounded-md bg-white/85 backdrop-blur border border-[var(--border)] p-0.5">
          <button
            onClick={zoomIn}
            className={cx(
              'w-7 h-7 grid place-items-center hover:bg-[var(--muted)] rounded text-[var(--muted-foreground)]'
            )}
            title="放大"
          >
            <ZoomInIcon className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={zoomOut}
            className="w-7 h-7 grid place-items-center hover:bg-[var(--muted)] rounded text-[var(--muted-foreground)]"
            title="缩小"
          >
            <ZoomOutIcon className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
}

// Helper for parent to focus a vehicle
export function focusMapOnVehicle(
  map: AMapMap | null,
  lng: number,
  lat: number
) {
  if (!map) return;
  map.setZoomAndCenter(16, [lng, lat]);
}
