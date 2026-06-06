import { useEffect, useMemo, useState } from 'react';
import { vehicleApi, type VehicleCreateRequest, type VehicleUpdateRequest } from '../../api/vehicle';
import { useAuth } from '../../stores/auth';
import type { Vehicle } from '../../types';
import { StatusBadge } from '../common/StatusBadge';
import { toast } from '../common/Toast';
import {
  PlusIcon,
  SearchIcon,
  CarFrontIcon,
  EyeIcon,
  EditIcon,
  TrashIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  BrainIcon,
} from '../common/Icons';
import { cx, fmtDate } from '../common/utils';
import { VehicleDialog } from './VehicleDialog';
import { ConfirmDialog } from './ConfirmDialog';
import { TelemetryInsightDialog } from './TelemetryInsightDialog';

type StatusFilter = 'all' | 'online' | 'offline';
type DialogMode = 'create' | 'edit' | 'view';

export function VehicleListView() {
  const { isAdmin } = useAuth();
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(8);
  const [total, setTotal] = useState(0);
  const [dialog, setDialog] = useState<DialogMode | null>(null);
  const [editing, setEditing] = useState<Vehicle | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<Vehicle | null>(null);
  const [insightVehicle, setInsightVehicle] = useState<Vehicle | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await vehicleApi.list(page, pageSize);
      setVehicles(res.records || []);
      setTotal(res.total || 0);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '加载车辆失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return vehicles.filter((v) => {
      if (statusFilter === 'online' && v.status !== 1) return false;
      if (statusFilter === 'offline' && v.status !== 0) return false;
      if (!q) return true;
      return (
        v.plateNo.toLowerCase().includes(q) ||
        (v.vin || '').toLowerCase().includes(q) ||
        (v.model || '').toLowerCase().includes(q)
      );
    });
  }, [vehicles, search, statusFilter]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));

  const handleCreate = async (payload: VehicleCreateRequest) => {
    try {
      await vehicleApi.create(payload);
      toast.success('新增成功');
      setDialog(null);
      setEditing(null);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '新增失败');
    }
  };

  const handleUpdate = async (id: number, payload: VehicleUpdateRequest) => {
    try {
      await vehicleApi.update(id, payload);
      toast.success('保存成功');
      setDialog(null);
      setEditing(null);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '保存失败');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await vehicleApi.remove(id);
      toast.success('删除成功');
      setConfirmDelete(null);
      load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '删除失败');
    }
  };

  return (
    <div className="view-in space-y-4">
      <header className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-[22px] font-semibold tracking-tight">车辆列表</h1>
          <p className="text-[13px] text-[var(--muted-foreground)] mt-1">
            共 {total} 台车辆 · {vehicles.filter((v) => v.status === 1).length} 台在线
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={() => {
              setEditing(null);
              setDialog('create');
            }}
            disabled={!isAdmin}
            title={!isAdmin ? '需要 ADMIN 权限' : '新增车辆'}
            className={cx(
              'h-8 px-3 rounded-md text-[12.5px] font-medium inline-flex items-center gap-1.5',
              isAdmin
                ? 'bg-[var(--primary)] text-[var(--primary-foreground)] hover:opacity-95'
                : 'bg-[var(--muted)] text-[var(--muted-foreground)] cursor-not-allowed'
            )}
          >
            <PlusIcon className="w-3.5 h-3.5" /> 新增车辆
          </button>
        </div>
      </header>

      <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] overflow-hidden">
        <div className="px-4 py-3 border-b border-[var(--border)] flex flex-col sm:flex-row sm:items-center gap-2.5 justify-between">
          <div className="flex items-center gap-2 flex-wrap">
            <div className="relative">
              <SearchIcon className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--muted-foreground)]" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="搜索车牌 / VIN / 车型"
                className="h-8 pl-8 pr-3 w-[240px] rounded-md border border-[var(--input)] bg-[var(--background)] text-[12.5px] outline-none focus:border-[var(--ring)] focus:ring-2 focus:ring-[var(--ring)]/15"
              />
            </div>
            <div className="flex items-center rounded-md border border-[var(--input)] bg-[var(--background)] p-0.5">
              {(['all', 'online', 'offline'] as const).map((s) => (
                <button
                  key={s}
                  onClick={() => setStatusFilter(s)}
                  className={cx(
                    'h-6 px-2 rounded text-[11.5px] font-medium transition-colors',
                    statusFilter === s
                      ? 'bg-[var(--primary)] text-[var(--primary-foreground)]'
                      : 'text-[var(--muted-foreground)] hover:text-[var(--foreground)]'
                  )}
                >
                  {s === 'all' ? '全部' : s === 'online' ? '在线' : '离线'}
                </button>
              ))}
            </div>
          </div>
          <div className="text-[11.5px] text-[var(--muted-foreground)] num">
            显示 {filtered.length} / {total} 条
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-[12.5px]">
            <thead>
              <tr className="bg-[var(--muted)]/40 border-b border-[var(--border)] text-[var(--muted-foreground)]">
                <th className="text-left font-medium px-4 py-2 w-14">ID</th>
                <th className="text-left font-medium px-4 py-2">车牌号</th>
                <th className="text-left font-medium px-4 py-2 hidden md:table-cell">VIN</th>
                <th className="text-left font-medium px-4 py-2 hidden sm:table-cell">车型</th>
                <th className="text-left font-medium px-4 py-2 w-20">状态</th>
                <th className="text-left font-medium px-4 py-2 hidden lg:table-cell w-40">创建时间</th>
                <th className="text-right font-medium px-4 py-2 w-36">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7} className="text-center py-16 text-[var(--muted-foreground)]">
                    加载中…
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-16 text-[var(--muted-foreground)]">
                    <div className="flex flex-col items-center gap-2">
                      <CarFrontIcon className="w-8 h-8 opacity-40" />
                      <div className="text-[12px]">
                        {isAdmin ? '暂无车辆，点击「新增车辆」添加' : '暂无匹配的车辆'}
                      </div>
                    </div>
                  </td>
                </tr>
              ) : (
                filtered.map((v) => (
                  <tr
                    key={v.id}
                    className="border-b border-[var(--border)]/60 hover:bg-[var(--muted)]/30 transition-colors"
                  >
                    <td className="px-4 py-2.5 num text-[var(--muted-foreground)]">#{v.id}</td>
                    <td className="px-4 py-2.5">
                      <div className="flex items-center gap-2">
                        <div className="w-7 h-7 rounded-md bg-[var(--accent)]/60 text-[var(--accent-foreground)] grid place-items-center">
                          <CarFrontIcon className="w-3.5 h-3.5" />
                        </div>
                        <span className="font-mono font-semibold tracking-tight">{v.plateNo}</span>
                      </div>
                    </td>
                    <td className="px-4 py-2.5 font-mono text-[11.5px] text-[var(--muted-foreground)] hidden md:table-cell">
                      {v.vin || '—'}
                    </td>
                    <td className="px-4 py-2.5 text-[var(--muted-foreground)] hidden sm:table-cell">
                      {v.model || '—'}
                    </td>
                    <td className="px-4 py-2.5">
                      <StatusBadge status={v.status} />
                    </td>
                    <td className="px-4 py-2.5 num text-[var(--muted-foreground)] hidden lg:table-cell">
                      {fmtDate(v.createdAt)}
                    </td>
                    <td className="px-4 py-2.5">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => setInsightVehicle(v)}
                          className="w-7 h-7 grid place-items-center rounded-md text-[var(--muted-foreground)] hover:bg-[var(--primary)]/10 hover:text-[var(--primary)] transition-colors"
                          title="AI 遥测诊断"
                        >
                          <BrainIcon className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => {
                            setEditing(v);
                            setDialog('view');
                          }}
                          className="w-7 h-7 grid place-items-center rounded-md text-[var(--muted-foreground)] hover:bg-[var(--muted)] hover:text-[var(--foreground)] transition-colors"
                          title="查看"
                        >
                          <EyeIcon className="w-3.5 h-3.5" />
                        </button>
                        {isAdmin && (
                          <>
                            <button
                              onClick={() => {
                                setEditing(v);
                                setDialog('edit');
                              }}
                              className="w-7 h-7 grid place-items-center rounded-md text-[var(--muted-foreground)] hover:bg-[var(--muted)] hover:text-[var(--foreground)] transition-colors"
                              title="编辑"
                            >
                              <EditIcon className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => setConfirmDelete(v)}
                              className="w-7 h-7 grid place-items-center rounded-md text-[var(--muted-foreground)] hover:bg-[var(--destructive)]/10 hover:text-[var(--destructive)] transition-colors"
                              title="删除"
                            >
                              <TrashIcon className="w-3.5 h-3.5" />
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {total > 0 && (
          <div className="px-4 py-3 border-t border-[var(--border)] flex items-center justify-between gap-3 flex-wrap">
            <div className="text-[11.5px] text-[var(--muted-foreground)] num">共 {total} 条</div>

            <div className="flex items-center gap-1">
              <button
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page === 1}
                className="w-7 h-7 grid place-items-center rounded-md border border-[var(--border)] disabled:opacity-40 hover:bg-[var(--muted)]"
              >
                <ChevronLeftIcon className="w-3.5 h-3.5" />
              </button>
              {Array.from({ length: totalPages }, (_, i) => i + 1)
                .filter((p) => p === 1 || p === totalPages || Math.abs(p - page) <= 1)
                .reduce<(number | '…')[]>((acc, p, i, arr) => {
                  if (i > 0 && (p as number) - (arr[i - 1] as number) > 1) acc.push('…');
                  acc.push(p);
                  return acc;
                }, [])
                .map((item, i) =>
                  item === '…' ? (
                    <span key={`e${i}`} className="px-1 text-[var(--muted-foreground)] text-[11.5px]">
                      …
                    </span>
                  ) : (
                    <button
                      key={item}
                      onClick={() => setPage(item)}
                      className={cx(
                        'min-w-7 h-7 px-2 grid place-items-center rounded-md text-[11.5px] font-medium',
                        item === page
                          ? 'bg-[var(--primary)] text-[var(--primary-foreground)]'
                          : 'border border-[var(--border)] hover:bg-[var(--muted)]'
                      )}
                    >
                      {item}
                    </button>
                  )
                )}
              <button
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                disabled={page === totalPages}
                className="w-7 h-7 grid place-items-center rounded-md border border-[var(--border)] disabled:opacity-40 hover:bg-[var(--muted)]"
              >
                <ChevronRightIcon className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>

      {dialog && (
        <VehicleDialog
          mode={dialog}
          vehicle={editing}
          onClose={() => {
            setDialog(null);
            setEditing(null);
          }}
          onSubmit={(payload) => {
            if (dialog === 'create') handleCreate(payload);
            if (dialog === 'edit' && editing) handleUpdate(editing.id, payload);
          }}
        />
      )}

      {confirmDelete && (
        <ConfirmDialog
          title="确认删除车辆？"
          message={`车牌 ${confirmDelete.plateNo} 将会被永久删除，相关遥测数据将无法再关联到该车辆。`}
          confirmText="删除"
          onCancel={() => setConfirmDelete(null)}
          onConfirm={() => handleDelete(confirmDelete.id)}
        />
      )}

      {insightVehicle && (
        <TelemetryInsightDialog
          vehicle={insightVehicle}
          onClose={() => setInsightVehicle(null)}
        />
      )}
    </div>
  );
}
