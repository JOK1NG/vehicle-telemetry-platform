import { useEffect, useMemo, useRef, useState } from 'react';
import { realtimeApi } from '../../api/realtime';
import { useVehicleSocket } from '../../hooks/useVehicleSocket';
import type { VehicleSnapshot, VehicleUpdateData } from '../../types';
import { MetricCard } from '../common/MetricCard';
import type { MarkerData } from './vehicleMarker';
import { toast } from '../common/Toast';
import {
  SignalIcon,
  GaugeIcon,
  BatteryIcon,
  ClockIcon,
  WifiIcon,
  WifiOffIcon,
  RefreshIcon,
} from '../common/Icons';
import { cx, fmtNum, fmtTime } from '../common/utils';
import { TelemetryMap, focusMapOnVehicle } from './TelemetryMap';
import { RealtimeList } from './RealtimeList';

export function DashboardView() {
  const [realtimeMap, setRealtimeMap] = useState<Map<number, VehicleUpdateData & { lastTs: number }>>(
    new Map()
  );
  const [lastUpdate, setLastUpdate] = useState<number>(Date.now());
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [snapshotLoading, setSnapshotLoading] = useState(false);
  const mapRef = useRef<Parameters<typeof focusMapOnVehicle>[0]>(null);

  const handleEnvelope = (vehicles: VehicleUpdateData[], ts?: string) => {
    setRealtimeMap((prev) => {
      const next = new Map(prev);
      const tsNum = ts ? new Date(ts).getTime() : Date.now();
      for (const v of vehicles) {
        next.set(v.vehicleId, { ...v, lastTs: tsNum });
      }
      return next;
    });
    setLastUpdate(Date.now());
  };

  const { wsConnected, wsError } = useVehicleSocket((envelope) => {
    handleEnvelope(envelope.vehicles, envelope.timestamp);
  });

  const loadSnapshot = async () => {
    setSnapshotLoading(true);
    try {
      const snapshots: VehicleSnapshot[] = await realtimeApi.snapshot();
      setRealtimeMap(
        new Map(
          snapshots.map((s) => [
            s.vehicleId,
            {
              vehicleId: s.vehicleId,
              plateNo: s.plateNo,
              lng: s.lng,
              lat: s.lat,
              speed: s.speed,
              heading: s.heading,
              battery: s.battery,
              status: s.status,
              lastTs: new Date(s.lastTs).getTime(),
            },
          ])
        )
      );
      setLastUpdate(Date.now());
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '获取快照失败');
    } finally {
      setSnapshotLoading(false);
    }
  };

  useEffect(() => {
    loadSnapshot();
  }, []);

  const realtimeList = useMemo(() => Array.from(realtimeMap.values()), [realtimeMap]);

  const online = realtimeList.filter((v) => v.status === 1);
  const avgSpeed = online.length ? online.reduce((s, v) => s + v.speed, 0) / online.length : 0;
  const avgBattery = online.length ? online.reduce((s, v) => s + v.battery, 0) / online.length : 0;
  const lowBattery = online.filter((v) => v.battery < 40).length;

  const handleSelect = (id: number | null) => {
    setSelectedId(id);
    if (id != null) {
      const v = realtimeMap.get(id);
      if (v) focusMapOnVehicle(mapRef.current, v.lng, v.lat);
    }
  };

  return (
    <div className="view-in space-y-4">
      <header className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-[22px] font-semibold tracking-tight">监控大屏</h1>
          <p className="text-[13px] text-[var(--muted-foreground)] mt-1">
            车辆快照 · WebSocket 实时更新 · 坐标全链路 GCJ-02
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <div
            className={cx(
              'inline-flex items-center gap-1.5 h-7 px-2.5 rounded-full text-[12px] font-medium border',
              wsConnected
                ? 'bg-[var(--chart-2)]/10 text-[var(--chart-2)] border-[var(--chart-2)]/25'
                : 'bg-[var(--destructive)]/10 text-[var(--destructive)] border-[var(--destructive)]/25'
            )}
          >
            <span
              className={cx(
                'relative inline-block w-1.5 h-1.5 rounded-full',
                wsConnected ? 'bg-[var(--chart-2)] live-dot' : 'bg-[var(--destructive)]'
              )}
            />
            {wsConnected ? '实时连接正常' : '实时连接断开'}
          </div>
          <button
            onClick={loadSnapshot}
            disabled={snapshotLoading}
            className="h-7 px-2.5 rounded-md text-[12px] font-medium border border-[var(--border)] bg-[var(--card)] hover:bg-[var(--muted)] inline-flex items-center gap-1.5 disabled:opacity-60"
          >
            <RefreshIcon className={cx('w-3.5 h-3.5', snapshotLoading && 'animate-spin')} />
            刷新
          </button>
        </div>
      </header>

      {wsError && (
        <div className="text-[12px] text-[var(--destructive)] bg-[var(--destructive)]/8 border border-[var(--destructive)]/20 rounded-md px-3 py-2">
          {wsError}
        </div>
      )}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <MetricCard
          icon={<SignalIcon className="w-3.5 h-3.5" />}
          label="在线车辆"
          value={String(online.length)}
          unit={`/ ${realtimeList.length}`}
          tone="primary"
        />
        <MetricCard
          icon={<GaugeIcon className="w-3.5 h-3.5" />}
          label="平均速度"
          value={fmtNum(avgSpeed)}
          unit="km/h"
        />
        <MetricCard
          icon={<BatteryIcon className="w-3.5 h-3.5" />}
          label="平均电量"
          value={fmtNum(avgBattery, 0)}
          unit="%"
        />
        <MetricCard
          icon={<ClockIcon className="w-3.5 h-3.5" />}
          label="最近更新"
          value={fmtTime(lastUpdate)}
          trend={lowBattery > 0 ? `${lowBattery} 低电量` : undefined}
        />
      </div>

      {/* aspect-[5/3] on the wrapper sets the row height from the map's anchor.
          The inner grid fills it; the list is stretched to the same height and
          scrolls internally with the fade + arrow indicator. */}
      <div className="aspect-[5/3] w-full">
        <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-3 h-full">
          <TelemetryMap
            realtime={realtimeList}
            selectedId={selectedId}
            onSelect={handleSelect}
            onMapReady={(m) => {
              mapRef.current = m;
            }}
          />
          <RealtimeList
            realtime={realtimeList}
            selectedId={selectedId}
            onSelect={(id) => handleSelect(id)}
          />
        </div>
      </div>
    </div>
  );
}
