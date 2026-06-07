import { useEffect, useRef, useState } from 'react';
import { useAlertsStore } from '../../stores/alerts';
import { useAlertsSocket } from '../../hooks/useAlertsSocket';
import { alertApi } from '../../api/alert';
import { BellIcon } from '../common/Icons';
import { AlertRow, AlertDetailDialog } from './AlertList';
import { cx } from '../common/utils';

/**
 * 顶栏铃铛 + 角标 + 下拉列表
 * - 订阅 /topic/alerts 实时入栈
 * - 启动时拉一次 /api/alerts/latest 补齐
 * - 点击"标记已读"或"清空"更新 lastSeenAt
 */
export function AlertBell() {
  const items = useAlertsStore((s) => s.items);
  const lastSeenAt = useAlertsStore((s) => s.lastSeenAt);
  const markAllSeen = useAlertsStore((s) => s.markAllSeen);
  const push = useAlertsStore((s) => s.push);
  const pushMany = useAlertsStore((s) => s.pushMany);

  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState<import('../../types').AlertItem | null>(null);
  const popoverRef = useRef<HTMLDivElement>(null);

  // 实时订阅
  useAlertsSocket((alert) => push(alert));

  // 启动补齐
  useEffect(() => {
    alertApi.latest(50).then((list) => {
      if (list.length) pushMany(list);
    }).catch(() => {});
  }, [pushMany]);

  // 点击外部关闭
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  // 打开时自动 markAllSeen
  const toggle = () => {
    setOpen((o) => {
      const next = !o;
      if (next) {
        // 等下一帧再 mark，确保 UI 先展开
        setTimeout(() => markAllSeen(), 0);
      }
      return next;
    });
  };

  // 角标（未读数）
  const unread = (() => {
    if (!lastSeenAt) return items.filter((a) => !a.handled).length;
    const since = new Date(lastSeenAt).getTime();
    return items.filter((a) => !a.handled && new Date(a.occurredAt).getTime() > since).length;
  })();

  return (
    <div className="relative" ref={popoverRef}>
      <button
        onClick={toggle}
        className="relative h-8 w-8 grid place-items-center rounded-md text-[var(--muted-foreground)] hover:text-[var(--foreground)] hover:bg-[var(--muted)] transition-colors"
        aria-label="告警"
      >
        <BellIcon className="w-4 h-4" />
        {unread > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-[var(--destructive)] text-white text-[10px] font-semibold flex items-center justify-center">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-2 w-[380px] rounded-xl border border-[var(--border)] bg-[var(--card)] shadow-lg z-50 modal-card-in">
          <div className="px-3 py-2.5 border-b border-[var(--border)] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-[13px] font-semibold">告警</span>
              {items.length > 0 && (
                <span className="text-[10.5px] text-[var(--muted-foreground)]">共 {items.length} 条</span>
              )}
            </div>
            <button
              onClick={() => markAllSeen()}
              className="text-[11px] text-[var(--muted-foreground)] hover:text-[var(--foreground)]"
            >
              全部标已读
            </button>
          </div>

          <div className="max-h-[420px] overflow-y-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden p-1.5">
            {items.length === 0 ? (
              <div className="py-10 text-center text-[12px] text-[var(--muted-foreground)]">
                暂无告警
              </div>
            ) : (
              <div className="space-y-0.5">
                {items.slice(0, 30).map((a) => (
                  <AlertRow key={a.id} alert={a} onClick={() => setDetail(a)} />
                ))}
              </div>
            )}
          </div>

          {items.length > 30 && (
            <div className="px-3 py-2 border-t border-[var(--border)] text-center text-[11px] text-[var(--muted-foreground)]">
              仅显示最近 30 条，完整列表见 <a href="/alerts" className="text-[var(--primary)]">告警页</a>
            </div>
          )}
        </div>
      )}

      {detail && <AlertDetailDialog alert={detail} onClose={() => setDetail(null)} />}
    </div>
  );
}
