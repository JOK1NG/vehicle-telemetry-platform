import { useEffect, useState } from 'react';
import { alertApi } from '../../api/alert';
import { useAlertsStore } from '../../stores/alerts';
import type { AlertItem, AlertLevel, AlertSeverity } from '../../types';
import { cx, fmtTime } from '../common/utils';
import { AlertTriangleIcon, BellIcon, RefreshIcon, XIcon } from '../common/Icons';
import { SeverityBadge } from '../common/SeverityBadge';
import { getAlertLabel, levelToSeverity } from '../../lib/alertRules';
import { AlertDetailDialog } from './AlertList';

const SEVERITY_FILTERS: { label: string; value: AlertLevel | 'ALL' }[] = [
  { label: '全部', value: 'ALL' },
  { label: 'LOW',      value: 1 },
  { label: 'MEDIUM',   value: 2 },
  { label: 'HIGH',     value: 3 },
  { label: 'CRITICAL', value: 4 },
];

export function AlertsView() {
  const items = useAlertsStore((s) => s.items);
  const pushMany = useAlertsStore((s) => s.pushMany);

  const [level, setLevel] = useState<AlertLevel | 'ALL'>('ALL');
  const [handled, setHandled] = useState<'all' | 'unhandled' | 'handled'>('unhandled');
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<AlertItem | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await alertApi.list({
        current: 1,
        size: 100,
        level: level === 'ALL' ? undefined : (level as AlertLevel),
        handled: handled === 'all' ? undefined : handled === 'handled',
      });
      pushMany(res.records);
    } catch (e) {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [level, handled]);

  return (
    <div className="view-in space-y-4">
      <header className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h1 className="text-[22px] font-semibold tracking-tight flex items-center gap-2">
            <BellIcon className="w-5 h-5 text-[var(--primary)]" />
            告警中心
          </h1>
          <p className="text-[13px] text-[var(--muted-foreground)] mt-1">
            实时订阅 <code className="font-mono text-[12px]">/topic/alerts</code>，共 {items.length} 条本地缓存
          </p>
        </div>
        <button
          onClick={load}
          disabled={loading}
          className="h-7 px-2.5 rounded-md text-[12px] font-medium border border-[var(--border)] bg-[var(--card)] hover:bg-[var(--muted)] inline-flex items-center gap-1.5 disabled:opacity-60"
        >
          <RefreshIcon className={cx('w-3.5 h-3.5', loading && 'animate-spin')} />
          刷新
        </button>
      </header>

      <div className="rounded-xl border border-[var(--border)] bg-[var(--card)]">
        <div className="px-4 py-3 border-b border-[var(--border)] flex items-center gap-3 flex-wrap">
          <div className="flex items-center gap-2">
            <span className="text-[11px] text-[var(--muted-foreground)]">级别</span>
            <div className="flex items-center rounded-md border border-[var(--input)] bg-[var(--background)] p-0.5">
              {SEVERITY_FILTERS.map((f) => (
                <button
                  key={f.value}
                  onClick={() => setLevel(f.value)}
                  className={cx(
                    'h-6 px-2 rounded text-[11.5px] font-medium transition-colors',
                    level === f.value
                      ? 'bg-[var(--primary)] text-[var(--primary-foreground)]'
                      : 'text-[var(--muted-foreground)] hover:text-[var(--foreground)]'
                  )}
                >
                  {f.label}
                </button>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[11px] text-[var(--muted-foreground)]">状态</span>
            <div className="flex items-center rounded-md border border-[var(--input)] bg-[var(--background)] p-0.5">
              {[
                { label: '未处理', value: 'unhandled' as const },
                { label: '已处理', value: 'handled' as const },
                { label: '全部',   value: 'all' as const },
              ].map((f) => (
                <button
                  key={f.value}
                  onClick={() => setHandled(f.value)}
                  className={cx(
                    'h-6 px-2 rounded text-[11.5px] font-medium transition-colors',
                    handled === f.value
                      ? 'bg-[var(--primary)] text-[var(--primary-foreground)]'
                      : 'text-[var(--muted-foreground)] hover:text-[var(--foreground)]'
                  )}
                >
                  {f.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="divide-y divide-[var(--border)]/60 max-h-[calc(100vh-260px)] overflow-y-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {items.length === 0 ? (
            <div className="px-4 py-16 text-center text-[12px] text-[var(--muted-foreground)]">
              {loading ? '加载中…' : '没有符合条件的告警'}
            </div>
          ) : (
            items.map((a) => (
              <button
                key={a.id}
                onClick={() => setDetail(a)}
                className="w-full text-left px-4 py-3 hover:bg-[var(--muted)]/40 flex items-start gap-3 transition-colors"
              >
                <AlertTriangleIcon className="w-4 h-4 mt-0.5 shrink-0 text-[var(--muted-foreground)]" />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-[13px] font-semibold">{getAlertLabel(a.type)}</span>
                    <SeverityBadge level={a.level} />
                    {a.handled && (
                      <span className="text-[10.5px] px-1.5 h-5 rounded-full bg-[var(--muted)] text-[var(--muted-foreground)] inline-flex items-center">
                        已处理
                      </span>
                    )}
                    <span className="text-[10.5px] text-[var(--muted-foreground)] ml-auto num">
                      {fmtTime(a.occurredAt)}
                    </span>
                  </div>
                  <div className="text-[12px] text-[var(--muted-foreground)] mt-0.5 line-clamp-2">{a.message}</div>
                  <div className="text-[10.5px] text-[var(--muted-foreground)] mt-1 font-mono">
                    {a.plateNo || `#${a.vehicleId}`}
                  </div>
                </div>
                <XIcon className="w-3.5 h-3.5 text-[var(--muted-foreground)] opacity-0 group-hover:opacity-100" />
              </button>
            ))
          )}
        </div>
      </div>

      {detail && <AlertDetailDialog alert={detail} onClose={() => setDetail(null)} />}
    </div>
  );
}
