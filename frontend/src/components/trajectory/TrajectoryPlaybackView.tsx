import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { vehicleApi } from '../../api/vehicle';
import { trajectoryApi } from '../../api/trajectory';
import { useAMap } from '../../hooks/useAMap';
import { Select } from '../common/Select';
import { cx, fmtTime } from '../common/utils';
import {
  PauseIcon,
  PlayIcon,
  RefreshIcon,
  RouteIcon,
  SkipBackIcon,
  SkipForwardIcon,
  VehiclesIcon,
} from '../common/Icons';
import type { TrajectoryPoint, Vehicle } from '../../types';

const SPEED_PRESETS = [1, 2, 4, 8] as const;

function startOfDay(d: Date): string {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x.toISOString().slice(0, 16);
}
function hoursAgo(n: number): string {
  return new Date(Date.now() - n * 3600_000).toISOString().slice(0, 16);
}
function nowLocal(): string {
  return new Date().toISOString().slice(0, 16);
}

export function TrajectoryPlaybackView() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialVehicleId = Number(searchParams.get('vehicleId') || '') || null;

  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [vehicleId, setVehicleId] = useState<number | null>(initialVehicleId);
  const [start, setStart] = useState(hoursAgo(24));
  const [end, setEnd] = useState(nowLocal());
  const [points, setPoints] = useState<TrajectoryPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState<typeof SPEED_PRESETS[number]>(1);
  const [cursor, setCursor] = useState(0); // index of current point

  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<unknown>(null);
  const polylineRef = useRef<unknown>(null);
  const startMarkerRef = useRef<unknown>(null);
  const endMarkerRef = useRef<unknown>(null);
  const cursorMarkerRef = useRef<unknown>(null);
  const rafRef = useRef<number | null>(null);
  const lastTickRef = useRef<number>(0);

  const { load: loadAMap, amapReady, createMap, getAMap } = useAMap();

  // 加载车辆列表
  useEffect(() => {
    vehicleApi.list(1, 100).then((page) => {
      setVehicles(page.records);
      if (!vehicleId && page.records.length > 0) {
        setVehicleId(page.records[0].id);
        const next = new URLSearchParams(searchParams);
        next.set('vehicleId', String(page.records[0].id));
        setSearchParams(next, { replace: true });
      }
    }).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 加载 AMap
  useEffect(() => {
    if (!mapContainerRef.current) return;
    loadAMap().then(() => {
      if (!mapContainerRef.current) return;
      if (!amapReady) return;
      const m = createMap(mapContainerRef.current, { zoom: 12, center: [121.473701, 31.230416] });
      if (m) mapRef.current = m;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [amapReady]);

  // 加载轨迹
  const loadTrajectory = async () => {
    if (!vehicleId) return;
    setLoading(true);
    setError(null);
    try {
      const startIso = new Date(start).toISOString();
      const endIso = new Date(end).toISOString();
      const data = await trajectoryApi.byVehicle(vehicleId, startIso, endIso, 2000);
      setPoints(data);
      setCursor(0);
      setPlaying(false);
      drawTrajectory(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  };

  // 画轨迹
  const drawTrajectory = (data: TrajectoryPoint[]) => {
    const AMap = getAMap();
    const map = mapRef.current as { setCenter: (c: [number, number]) => void; setZoom: (z: number) => void; remove: (o: unknown) => void; add: (o: unknown) => void } | null;
    if (!AMap || !map || data.length === 0) return;

    // 清理旧的
    [polylineRef.current, startMarkerRef.current, endMarkerRef.current, cursorMarkerRef.current].forEach((o) => {
      if (o) map.remove(o);
    });

    const path = data.map((p) => [p.lng, p.lat] as [number, number]);

    // 折线（按速度着色：先简化用单色）
    const AMapAny = AMap as { Polyline: new (opts: object) => unknown; Marker: new (opts: object) => unknown };
    polylineRef.current = new AMapAny.Polyline({
      path,
      strokeColor: '#0ea5e9',
      strokeWeight: 4,
      strokeOpacity: 0.85,
      lineJoin: 'round',
    });
    map.add(polylineRef.current);

    // 起点/终点
    const first = data[0];
    const last = data[data.length - 1];
    startMarkerRef.current = new AMapAny.Marker({
      position: [first.lng, first.lat],
      content: '<div style="width:14px;height:14px;border-radius:50%;background:var(--chart-2,#22c55e);border:2px solid #fff;box-shadow:0 0 0 1px rgba(0,0,0,.2)"></div>',
      offset: { x: -7, y: -7 } as unknown as never,
    });
    endMarkerRef.current = new AMapAny.Marker({
      position: [last.lng, last.lat],
      content: '<div style="width:14px;height:14px;border-radius:50%;background:var(--destructive,#ef4444);border:2px solid #fff;box-shadow:0 0 0 1px rgba(0,0,0,.2)"></div>',
      offset: { x: -7, y: -7 } as unknown as never,
    });
    map.add(startMarkerRef.current);
    map.add(endMarkerRef.current);

    // 当前位置 marker
    cursorMarkerRef.current = new AMapAny.Marker({
      position: [first.lng, first.lat],
      content: '<div style="width:18px;height:18px;border-radius:50%;background:var(--primary,#0ea5e9);border:3px solid #fff;box-shadow:0 0 0 2px rgba(14,165,233,.4)"></div>',
      offset: { x: -9, y: -9 } as unknown as never,
    });
    map.add(cursorMarkerRef.current);

    // 居中
    map.setCenter([first.lng, first.lat]);
    map.setZoom(13);
  };

  // 播放
  useEffect(() => {
    if (!playing || points.length === 0) {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
      return;
    }
    const step = () => {
      const now = performance.now();
      if (now - lastTickRef.current < 1000 / speed) {
        rafRef.current = requestAnimationFrame(step);
        return;
      }
      lastTickRef.current = now;
      setCursor((c) => {
        if (c >= points.length - 1) {
          setPlaying(false);
          return c;
        }
        const next = c + 1;
        const p = points[next];
        const marker = cursorMarkerRef.current as { setPosition: (pos: [number, number]) => void } | null;
        if (marker) marker.setPosition([p.lng, p.lat]);
        return next;
      });
      rafRef.current = requestAnimationFrame(step);
    };
    rafRef.current = requestAnimationFrame(step);
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [playing, speed, points]);

  const onVehicleChange = (id: number) => {
    setVehicleId(id);
    const next = new URLSearchParams(searchParams);
    next.set('vehicleId', String(id));
    setSearchParams(next, { replace: true });
  };

  const currentPoint = useMemo(() => points[cursor] ?? null, [points, cursor]);
  const progressPct = points.length > 0 ? (cursor / (points.length - 1)) * 100 : 0;
  const vehicleOptions = useMemo(
    () =>
      vehicles.map((v) => ({
        value: v.id,
        label: v.plateNo,
        description: v.model ? `(${v.model})` : undefined,
      })),
    [vehicles]
  );

  return (
    <div className="view-in space-y-4">
      <header className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-[22px] font-semibold tracking-tight flex items-center gap-2">
            <RouteIcon className="w-5 h-5 text-[var(--primary)]" />
            轨迹回放
          </h1>
          <p className="text-[13px] text-[var(--muted-foreground)] mt-1">
            时间窗口 ≤ 7 天，采样自动均匀抽稀到 2000 点以内
          </p>
        </div>
      </header>

      <div className="grid grid-cols-1 xl:grid-cols-[320px_1fr] gap-3">
        {/* 左侧：车辆 + 时间选择 */}
        <div className="space-y-3">
          <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-3 space-y-2">
            <div className="text-[12px] font-semibold flex items-center gap-1.5">
              <VehiclesIcon className="w-3.5 h-3.5" /> 选择车辆
            </div>
            <Select
              value={vehicleId}
              onChange={onVehicleChange}
              options={vehicleOptions}
              loading={vehicles.length === 0}
              placeholder="选择车辆"
            />
          </div>

          <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-3 space-y-2">
            <div className="text-[12px] font-semibold">时间范围</div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="text-[10.5px] text-[var(--muted-foreground)]">开始</label>
                <input
                  type="datetime-local"
                  value={start}
                  onChange={(e) => setStart(e.target.value)}
                  className="w-full h-8 px-2 rounded-md border border-[var(--input)] bg-[var(--background)] text-[12px]"
                />
              </div>
              <div>
                <label className="text-[10.5px] text-[var(--muted-foreground)]">结束</label>
                <input
                  type="datetime-local"
                  value={end}
                  onChange={(e) => setEnd(e.target.value)}
                  className="w-full h-8 px-2 rounded-md border border-[var(--input)] bg-[var(--background)] text-[12px]"
                />
              </div>
            </div>
            <div className="flex gap-1.5 flex-wrap">
              {[1, 24, 24 * 7].map((h) => (
                <button
                  key={h}
                  onClick={() => { setStart(hoursAgo(h)); setEnd(nowLocal()); }}
                  className="h-6 px-2 rounded text-[11px] border border-[var(--border)] hover:bg-[var(--muted)]"
                >
                  {h < 24 ? `近${h}小时` : h === 24 ? '近24小时' : '近7天'}
                </button>
              ))}
            </div>
            <button
              onClick={loadTrajectory}
              disabled={loading || !vehicleId}
              className="w-full h-8 rounded-md bg-[var(--primary)] text-[var(--primary-foreground)] text-[12px] font-medium inline-flex items-center justify-center gap-1.5 disabled:opacity-60"
            >
              <RefreshIcon className={cx('w-3.5 h-3.5', loading && 'animate-spin')} />
              {loading ? '加载中…' : '加载轨迹'}
            </button>
            {error && (
              <div className="text-[11px] text-[var(--destructive)]">{error}</div>
            )}
          </div>
        </div>

        {/* 右侧：地图 + 播放控制 */}
        <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden flex flex-col">
          <div ref={mapContainerRef} className="w-full flex-1 min-h-[420px] bg-[var(--muted)]" />

          {/* 播放条 */}
          <div className="border-t border-[var(--border)] p-3 space-y-2">
            <div className="flex items-center gap-3">
              <button
                onClick={() => setCursor(0)}
                className="w-8 h-8 grid place-items-center rounded-md hover:bg-[var(--muted)]"
                title="回到起点"
              >
                <SkipBackIcon className="w-4 h-4" />
              </button>
              <button
                onClick={() => setPlaying((p) => !p)}
                disabled={points.length === 0}
                className="w-9 h-9 grid place-items-center rounded-full bg-[var(--primary)] text-[var(--primary-foreground)] disabled:opacity-50"
              >
                {playing ? <PauseIcon className="w-4 h-4" /> : <PlayIcon className="w-4 h-4" />}
              </button>
              <button
                onClick={() => setCursor(points.length - 1)}
                className="w-8 h-8 grid place-items-center rounded-md hover:bg-[var(--muted)]"
                title="跳到终点"
              >
                <SkipForwardIcon className="w-4 h-4" />
              </button>

              <div className="flex-1 mx-2">
                <input
                  type="range"
                  min={0}
                  max={Math.max(0, points.length - 1)}
                  value={cursor}
                  onChange={(e) => {
                    setCursor(Number(e.target.value));
                    setPlaying(false);
                    const p = points[Number(e.target.value)];
                    if (p) {
                      const m = cursorMarkerRef.current as { setPosition: (pos: [number, number]) => void } | null;
                      if (m) m.setPosition([p.lng, p.lat]);
                    }
                  }}
                  className="w-full accent-[var(--primary)]"
                />
                <div className="text-[10.5px] text-[var(--muted-foreground)] flex justify-between mt-0.5">
                  <span>{points.length > 0 ? `点 ${cursor + 1}/${points.length}` : '—'}</span>
                  <span className="num">{progressPct.toFixed(0)}%</span>
                </div>
              </div>

              <div className="flex items-center gap-1 rounded-md border border-[var(--input)] p-0.5">
                {SPEED_PRESETS.map((s) => (
                  <button
                    key={s}
                    onClick={() => setSpeed(s)}
                    className={cx(
                      'h-7 px-2 rounded text-[11px] font-medium',
                      speed === s
                        ? 'bg-[var(--primary)] text-[var(--primary-foreground)]'
                        : 'hover:bg-[var(--muted)]'
                    )}
                  >
                    {s}x
                  </button>
                ))}
              </div>
            </div>

            {currentPoint && (
              <div className="grid grid-cols-3 gap-2 text-center text-[12px]">
                <div className="rounded-md bg-[var(--muted)]/50 py-1.5">
                  <div className="text-[10px] text-[var(--muted-foreground)]">速度</div>
                  <div className="num font-semibold">{currentPoint.speed.toFixed(1)} km/h</div>
                </div>
                <div className="rounded-md bg-[var(--muted)]/50 py-1.5">
                  <div className="text-[10px] text-[var(--muted-foreground)]">电量</div>
                  <div className="num font-semibold">{currentPoint.battery.toFixed(0)}%</div>
                </div>
                <div className="rounded-md bg-[var(--muted)]/50 py-1.5">
                  <div className="text-[10px] text-[var(--muted-foreground)]">时间</div>
                  <div className="num text-[11px]">{fmtTime(currentPoint.time)}</div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
