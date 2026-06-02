import { cx } from './utils';

export function StatusBadge({ status }: { status: number }) {
  const online = status === 1;
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 h-5 px-1.5 rounded text-[10.5px] font-medium',
        online
          ? 'bg-[var(--chart-2)]/12 text-[var(--chart-2)]'
          : 'bg-[var(--muted)] text-[var(--muted-foreground)]'
      )}
    >
      <span
        className={cx(
          'w-1.5 h-1.5 rounded-full',
          online ? 'bg-[var(--chart-2)]' : 'bg-[var(--muted-foreground)]/50'
        )}
      />
      {online ? '在线' : '离线'}
    </span>
  );
}
