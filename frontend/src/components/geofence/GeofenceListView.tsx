import { useEffect, useRef, useState } from 'react';
import { geofenceApi } from '../../api/geofence';
import { vehicleApi } from '../../api/vehicle';
import { useAMap } from '../../hooks/useAMap';
import { cx } from '../common/utils';
import { ModalShell } from '../common/ModalShell';
import { ConfirmDialog } from '../vehicles/ConfirmDialog';
import { FenceIcon, PlusIcon, TrashIcon, XIcon } from '../common/Icons';
import type { Geofence, LngLat, GeofenceType, Vehicle } from '../../types';
import { toast } from '../common/Toast';

const SHANGHAI_CENTER: [number, number] = [121.473701, 31.230416];

export function GeofenceListView() {
  const [items, setItems] = useState<Geofence[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [editing, setEditing] = useState<Geofence | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<Geofence | null>(null);
  const [drawMode, setDrawMode] = useState<GeofenceType | null>(null);
  const [loading, setLoading] = useState(false);

  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<unknown>(null);
  const overlaysRef = useRef<Map<number, unknown>>(new Map());

  const { load: loadAMap, amapReady, createMap, getAMap } = useAMap();

  // 加载数据
  const load = async () => {
    setLoading(true);
    try {
      const [list, vs] = await Promise.all([geofenceApi.list(), vehicleApi.list(1, 200)]);
      setItems(list);
      setVehicles(vs.records);
      drawAll(list);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 初始化地图
  useEffect(() => {
    if (!mapContainerRef.current) return;
    loadAMap().then(() => {
      if (!mapContainerRef.current || !amapReady) return;
      const m = createMap(mapContainerRef.current, { zoom: 11, center: SHANGHAI_CENTER });
      if (m) {
        mapRef.current = m;
        drawAll(items);
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [amapReady]);

  const drawAll = (list: Geofence[]) => {
    const AMap = getAMap();
    const map = mapRef.current as { remove: (o: unknown) => void; add: (o: unknown) => void; setFitView: () => void } | null;
    if (!AMap || !map) return;
    // 清理旧
    overlaysRef.current.forEach((o) => map.remove(o));
    overlaysRef.current.clear();
    const AMapAny = AMap as { Circle: new (opts: object) => unknown; Polygon: new (opts: object) => unknown };

    list.forEach((g) => {
      if (!g.enabled) return;
      let overlay: unknown;
      if (g.type === 'CIRCLE' && g.centerLng != null && g.centerLat != null && g.radiusM) {
        overlay = new AMapAny.Circle({
          center: [g.centerLng, g.centerLat],
          radius: g.radiusM,
          strokeColor: '#0ea5e9',
          strokeWeight: 2,
          strokeOpacity: 0.9,
          fillColor: '#0ea5e9',
          fillOpacity: 0.1,
        });
      } else if (g.type === 'POLYGON' && g.polygon && g.polygon.length >= 3) {
        overlay = new AMapAny.Polygon({
          path: g.polygon.map((p) => [p.lng, p.lat] as [number, number]),
          strokeColor: '#0ea5e9',
          strokeWeight: 2,
          fillColor: '#0ea5e9',
          fillOpacity: 0.1,
        });
      }
      if (overlay) {
        map.add(overlay);
        overlaysRef.current.set(g.id, overlay);
      }
    });
  };

  // 点击地图 = 绘制模式下加顶点
  const onMapClick = async (e: unknown) => {
    if (!drawMode) return;
    const lnglat = (e as { lnglat: { lng: number; lat: number } }).lnglat;
    if (drawMode === 'CIRCLE') {
      // 圆形：点击为圆心，弹窗输入半径
      setEditing({
        id: 0,
        name: '新圆形围栏',
        type: 'CIRCLE',
        centerLng: lnglat.lng,
        centerLat: lnglat.lat,
        radiusM: 1000,
        polygon: null,
        enabled: true,
        vehicleIds: [],
      });
      setDrawMode(null);
    } else {
      // 多边形：累积顶点
      setEditing((prev) => {
        const polygon = prev?.type === 'POLYGON' && prev.polygon ? [...prev.polygon, { lng: lnglat.lng, lat: lnglat.lat }] : [{ lng: lnglat.lng, lat: lnglat.lat }];
        return {
          id: 0,
          name: prev?.name || '新多边形围栏',
          type: 'POLYGON',
          centerLng: null,
          centerLat: null,
          radiusM: null,
          polygon,
          enabled: true,
          vehicleIds: prev?.vehicleIds || [],
        };
      });
    }
  };

  useEffect(() => {
    const map = mapRef.current as { on: (event: string, handler: (e: unknown) => void) => void; off: (event: string, handler: (e: unknown) => void) => void } | null;
    if (!map) return;
    if (drawMode) {
      map.on('click', onMapClick);
    }
    return () => {
      if (map) map.off('click', onMapClick);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [drawMode]);

  // 保存围栏
  const save = async (g: Geofence) => {
    if (!g.name.trim()) {
      toast.error('请输入围栏名称');
      return;
    }
    try {
      if (g.id === 0) {
        const created = await geofenceApi.create({
          name: g.name,
          type: g.type,
          centerLng: g.centerLng ?? undefined,
          centerLat: g.centerLat ?? undefined,
          radiusM: g.radiusM ?? undefined,
          polygon: g.polygon ?? undefined,
          vehicleIds: g.vehicleIds,
        });
        toast.success('创建成功');
        setSelectedId(created.id);
      } else {
        await geofenceApi.update(g.id, g);
        toast.success('已更新');
      }
      setEditing(null);
      setDrawMode(null);
      await load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '保存失败');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await geofenceApi.remove(id);
      toast.success('已删除');
      setConfirmDelete(null);
      if (selectedId === id) setSelectedId(null);
      await load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '删除失败');
    }
  };

  return (
    <div className="view-in space-y-4">
      <header className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-[22px] font-semibold tracking-tight flex items-center gap-2">
            <FenceIcon className="w-5 h-5 text-[var(--primary)]" />
            地理围栏
          </h1>
          <p className="text-[13px] text-[var(--muted-foreground)] mt-1">
            圆形/多边形围栏管理；车辆进出会自动触发 <code className="font-mono text-[12px]">GEOFENCE_ENTER/EXIT</code> 告警
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={() => { setDrawMode('CIRCLE'); setEditing(null); toast.info('点击地图设置圆形围栏中心点'); }}
            className={cx('h-8 px-3 rounded-md text-[12px] font-medium border inline-flex items-center gap-1.5',
              drawMode === 'CIRCLE'
                ? 'bg-[var(--primary)] text-[var(--primary-foreground)] border-[var(--primary)]'
                : 'border-[var(--border)] bg-[var(--card)] hover:bg-[var(--muted)]')}
          >
            <PlusIcon className="w-3.5 h-3.5" /> 画圆形
          </button>
          <button
            onClick={() => { setDrawMode('POLYGON'); setEditing(null); toast.info('依次点击地图添加多边形顶点'); }}
            className={cx('h-8 px-3 rounded-md text-[12px] font-medium border inline-flex items-center gap-1.5',
              drawMode === 'POLYGON'
                ? 'bg-[var(--primary)] text-[var(--primary-foreground)] border-[var(--primary)]'
                : 'border-[var(--border)] bg-[var(--card)] hover:bg-[var(--muted)]')}
          >
            <PlusIcon className="w-3.5 h-3.5" /> 画多边形
          </button>
        </div>
      </header>

      <div className="grid grid-cols-1 xl:grid-cols-[320px_1fr] gap-3">
        {/* 左：围栏列表 */}
        <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
          <div className="px-4 py-3 border-b border-[var(--border)] flex items-center justify-between">
            <span className="text-[12px] font-semibold">围栏列表 ({items.length})</span>
          </div>
          <div className="divide-y divide-[var(--border)]/60 max-h-[calc(100vh-260px)] overflow-y-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {items.length === 0 && (
              <div className="px-4 py-12 text-center text-[12px] text-[var(--muted-foreground)]">
                {loading ? '加载中…' : '还没有围栏，点击右上角"画圆形/画多边形"开始'}
              </div>
            )}
            {items.map((g) => (
              <div
                key={g.id}
                className={cx(
                  'px-4 py-2.5 flex items-center gap-2 cursor-pointer',
                  selectedId === g.id ? 'bg-[var(--accent)]/40' : 'hover:bg-[var(--muted)]/40'
                )}
                onClick={() => setSelectedId(g.id)}
              >
                <FenceIcon className="w-3.5 h-3.5 text-[var(--primary)] shrink-0" />
                <div className="flex-1 min-w-0">
                  <div className="text-[12.5px] font-medium truncate">{g.name}</div>
                  <div className="text-[10.5px] text-[var(--muted-foreground)]">
                    {g.type === 'CIRCLE' ? `圆形 · 半径 ${g.radiusM}m` : `多边形 · ${g.polygon?.length ?? 0} 顶点`}
                    {' · '}
                    {g.enabled ? '启用' : '禁用'}
                    {g.vehicleIds.length > 0 ? ` · ${g.vehicleIds.length} 车` : ' · 全部车辆'}
                  </div>
                </div>
                <button
                  onClick={(e) => { e.stopPropagation(); setEditing(g); }}
                  className="text-[11px] text-[var(--primary)] hover:underline"
                >
                  编辑
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); setConfirmDelete(g); }}
                  className="text-[var(--muted-foreground)] hover:text-[var(--destructive)]"
                  title="删除"
                >
                  <TrashIcon className="w-3.5 h-3.5" />
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* 右：地图 */}
        <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
          <div ref={mapContainerRef} className="w-full h-[calc(100vh-200px)] min-h-[480px] bg-[var(--muted)]" />
        </div>
      </div>

      {editing && (
        <GeofenceEditor
          geofence={editing}
          vehicles={vehicles}
          onCancel={() => { setEditing(null); setDrawMode(null); }}
          onSave={save}
        />
      )}

      {confirmDelete && (
        <ConfirmDialog
          title="确认删除围栏？"
          message={`围栏「${confirmDelete.name}」将会被永久删除，关联的进出告警规则也将失效。`}
          confirmText="删除"
          onCancel={() => setConfirmDelete(null)}
          onConfirm={() => handleDelete(confirmDelete.id)}
        />
      )}
    </div>
  );
}

function GeofenceEditor({
  geofence,
  vehicles,
  onCancel,
  onSave,
}: {
  geofence: Geofence;
  vehicles: Vehicle[];
  onCancel: () => void;
  onSave: (g: Geofence) => void;
}) {
  const [name, setName] = useState(geofence.name);
  const [radius, setRadius] = useState(geofence.radiusM ?? 1000);
  const [enabled, setEnabled] = useState(geofence.enabled);
  const [vehicleIds, setVehicleIds] = useState<number[]>(geofence.vehicleIds);

  return (
    <ModalShell onClose={onCancel} size="xl">
      {(requestClose) => (
        <>
        <div className="px-4 py-3 border-b border-[var(--border)] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <FenceIcon className="w-4 h-4 text-[var(--primary)]" />
            <span className="text-[14px] font-semibold">{geofence.id === 0 ? '新建围栏' : '编辑围栏'}</span>
          </div>
          <button onClick={requestClose} className="w-7 h-7 grid place-items-center rounded-md hover:bg-[var(--muted)]">
            <XIcon className="w-4 h-4" />
          </button>
        </div>

        <div className="px-4 py-4 space-y-3 max-h-[60vh] overflow-y-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          <div>
            <label className="text-[11px] text-[var(--muted-foreground)]">名称</label>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mt-1 w-full h-8 px-2 rounded-md border border-[var(--input)] bg-[var(--background)] text-[12.5px]"
            />
          </div>

          {geofence.type === 'CIRCLE' && (
            <div>
              <label className="text-[11px] text-[var(--muted-foreground)]">半径（米）</label>
              <input
                type="number"
                value={radius}
                onChange={(e) => setRadius(Number(e.target.value))}
                min={100}
                step={100}
                className="mt-1 w-full h-8 px-2 rounded-md border border-[var(--input)] bg-[var(--background)] text-[12.5px]"
              />
            </div>
          )}

          {geofence.type === 'POLYGON' && geofence.polygon && (
            <div>
              <label className="text-[11px] text-[var(--muted-foreground)]">顶点 ({geofence.polygon.length})</label>
              <div className="mt-1 max-h-32 overflow-y-auto text-[11px] font-mono p-2 rounded-md bg-[var(--muted)]/40">
                {geofence.polygon.map((p, i) => (
                  <div key={i}>{i + 1}. {p.lng.toFixed(5)}, {p.lat.toFixed(5)}</div>
                ))}
              </div>
            </div>
          )}

          <div>
            <label className="flex items-center gap-2 text-[12px] cursor-pointer">
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
                className="accent-[var(--primary)]"
              />
              启用此围栏
            </label>
          </div>

          <div>
            <label className="text-[11px] text-[var(--muted-foreground)]">
              适用车辆（{vehicleIds.length > 0 ? `${vehicleIds.length}` : '全部'}）
            </label>
            <div className="mt-1 max-h-40 overflow-y-auto border border-[var(--border)] rounded-md p-2 space-y-1">
              {vehicles.length === 0 && <div className="text-[11px] text-[var(--muted-foreground)]">加载中…</div>}
              {vehicles.map((v) => (
                <label key={v.id} className="flex items-center gap-2 text-[12px] cursor-pointer">
                  <input
                    type="checkbox"
                    checked={vehicleIds.includes(v.id)}
                    onChange={(e) => {
                      if (e.target.checked) setVehicleIds((arr) => [...arr, v.id]);
                      else setVehicleIds((arr) => arr.filter((x) => x !== v.id));
                    }}
                    className="accent-[var(--primary)]"
                  />
                  <span className="font-mono">{v.plateNo}</span>
                  <span className="text-[10.5px] text-[var(--muted-foreground)]">{v.model}</span>
                </label>
              ))}
            </div>
          </div>
        </div>

        <div className="px-4 py-3 border-t border-[var(--border)] flex items-center justify-end gap-2">
          <button
            onClick={requestClose}
            className="h-8 px-3 rounded-md text-[12px] font-medium border border-[var(--border)] bg-[var(--card)] hover:bg-[var(--muted)]"
          >
            取消
          </button>
          <button
            onClick={() => onSave({
              ...geofence, name, radiusM: geofence.type === 'CIRCLE' ? radius : null, enabled, vehicleIds,
            })}
            className="h-8 px-3 rounded-md text-[12px] font-medium bg-[var(--primary)] text-[var(--primary-foreground)] hover:opacity-95"
          >
            保存
          </button>
        </div>
        </>
      )}
    </ModalShell>
  );
}
