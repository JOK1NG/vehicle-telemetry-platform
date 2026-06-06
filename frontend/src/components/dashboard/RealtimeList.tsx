import { useEffect, useMemo, useRef, useState } from 'react';
import type { VehicleUpdateData } from '../../types';
import { ChevronDownIcon } from '../common/Icons';
import { cx, fmtNum, fmtTime } from '../common/utils';

export function RealtimeList({
  realtime,
  selectedId,
  onSelect,
}: {
  realtime: VehicleUpdateData[];
  selectedId: number | null;
  onSelect: (id: number) => void;
}) {
  const sorted = useMemo(
    () => [...realtime].sort((a, b) => b.speed - a.speed),
    [realtime]
  );
  const listRef = useRef<HTMLDivElement>(null);
  const [showScrollHint, setShowScrollHint] = useState(false);

  useEffect(() => {
    const el = listRef.current;
    if (!el) return;
    const check = () => {
      const hasOverflow = el.scrollHeight - el.clientHeight > 1;
      const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 1;
      setShowScrollHint(hasOverflow && !atBottom);
    };
    check();
    el.addEventListener('scroll', check);
    const ro = new ResizeObserver(check);
    ro.observe(el);
    return () => {
      el.removeEventListener('scroll', check);
      ro.disconnect();
    };
  }, [sorted.length]);

  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] flex flex-col h-full min-h-0">
      <div className="px-4 py-3 border-b border-[var(--border)] flex items-center justify-between">
        <div>
          <div className="text-[13px] font-semibold">在线车辆</div>
          <div className="text-[11px] text-[var(--muted-foreground)]">{realtime.length} 台 · 按速度排序</div>
        </div>
      </div>
      <div className="relative flex-1 min-h-0">
        <div
          ref={listRef}
          className="h-full overflow-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          {sorted.length === 0 ? (
            <div className="px-4 py-12 text-center text-[12px] text-[var(--muted-foreground)]">
              暂无在线车辆
            </div>
          ) : (
            <ul className="divide-y divide-[var(--border)] w-full h-full flex flex-col">
              {sorted.map((v) => {
                const active = selectedId === v.vehicleId;
                const ts = (v as VehicleUpdateData & { lastTs?: number | string }).lastTs;
                return (
                  <li
                    key={v.vehicleId}
                    onClick={() => onSelect(v.vehicleId)}
                    className={cx(
                      'flex-1 min-h-[64px] flex flex-col justify-center px-4 cursor-pointer transition-colors min-w-0',
                      active ? 'bg-[var(--accent)]/40' : 'hover:bg-[var(--muted)]/50'
                    )}
                  >
                    <div className="flex items-center justify-between gap-2 w-full min-w-0">
                      <div className="flex items-center gap-2 min-w-0 flex-1">
                        <span
                          className={cx(
                            'w-1.5 h-1.5 rounded-full shrink-0',
                            v.status === 1 ? 'bg-[var(--chart-2)]' : 'bg-[var(--muted-foreground)]/40'
                          )}
                        />
                        <span className="text-[12.5px] font-mono font-semibold truncate min-w-0">
                          {v.plateNo}
                        </span>
                      </div>
                      <span className="text-[10px] num text-[var(--muted-foreground)] shrink-0">
                        {ts ? fmtTime(ts) : '-'}
                      </span>
                    </div>
                    <div className="mt-1 grid grid-cols-3 gap-2 text-[10.5px] w-full">
                      <div className="min-w-0">
                        <div className="text-[var(--muted-foreground)]">速度</div>
                        <div className="num font-semibold truncate">
                          {fmtNum(v.speed)}{' '}
                          <span className="text-[9px] font-normal text-[var(--muted-foreground)]">
                            km/h
                          </span>
                        </div>
                      </div>
                      <div className="min-w-0">
                        <div className="text-[var(--muted-foreground)]">电量</div>
                        <div className="num font-semibold truncate">{fmtNum(v.battery, 0)}%</div>
                      </div>
                      <div className="min-w-0">
                        <div className="text-[var(--muted-foreground)]">朝向</div>
                        <div className="num font-semibold truncate">{fmtNum(v.heading, 0)}°</div>
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
        {showScrollHint && (
          <div className="pointer-events-none absolute bottom-0 left-0 right-0 h-10 bg-gradient-to-t from-[var(--card)] via-[var(--card)]/70 to-transparent flex items-end justify-center pb-1.5">
            <ChevronDownIcon className="w-3.5 h-3.5 text-[var(--muted-foreground)] scroll-hint" />
          </div>
        )}
      </div>
    </div>
  );
}
