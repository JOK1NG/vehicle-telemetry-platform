import type { ReactNode } from 'react';
import { cx } from './utils';

export function MetricCard({
  icon,
  label,
  value,
  unit,
  trend,
  tone = 'default',
}: {
  icon: ReactNode;
  label: string;
  value: string;
  unit?: string;
  trend?: string;
  tone?: 'default' | 'primary';
}) {
  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-4 flex flex-col gap-2 hover:border-[var(--ring)]/40 transition-colors">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-[var(--muted-foreground)]">
          <span
            className={cx(
              'w-7 h-7 rounded-md grid place-items-center',
              tone === 'primary'
                ? 'bg-[var(--primary)]/12 text-[var(--primary)]'
                : 'bg-[var(--muted)] text-[var(--muted-foreground)]'
            )}
          >
            {icon}
          </span>
          <span className="text-[12px] font-medium">{label}</span>
        </div>
        {trend && (
          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-[var(--chart-2)]/10 text-[var(--chart-2)]">
            {trend}
          </span>
        )}
      </div>
      <div className="flex items-baseline gap-1.5">
        <span className="num text-[26px] font-semibold leading-none tracking-tight">{value}</span>
        {unit && <span className="text-[12px] text-[var(--muted-foreground)]">{unit}</span>}
      </div>
    </div>
  );
}
