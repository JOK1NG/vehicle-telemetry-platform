import { useEffect, useRef, useState } from 'react';
import { CheckIcon, ChevronDownIcon } from './Icons';
import { cx } from './utils';

export type SelectOption<T extends string | number> = {
  value: T;
  label: string;
  description?: string;
};

export function Select<T extends string | number>({
  value,
  onChange,
  options,
  placeholder = '请选择',
  loading = false,
  disabled = false,
}: {
  value: T | null | '';
  onChange: (value: T) => void;
  options: SelectOption<T>[];
  placeholder?: string;
  loading?: boolean;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selected = options.find((o) => o.value === value);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    const onClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    window.addEventListener('keydown', onKey);
    document.addEventListener('mousedown', onClick);
    return () => {
      window.removeEventListener('keydown', onKey);
      document.removeEventListener('mousedown', onClick);
    };
  }, [open]);

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        disabled={disabled || loading}
        onClick={() => setOpen((o) => !o)}
        className={cx(
          'w-full h-9 px-2.5 rounded-md border border-[var(--input)] bg-[var(--background)] text-[12.5px]',
          'flex items-center gap-2 transition-colors',
          'hover:bg-[var(--muted)]/30 focus:outline-none focus:border-[var(--ring)] focus:ring-2 focus:ring-[var(--ring)]/15',
          disabled && 'opacity-60 cursor-not-allowed',
          open && 'border-[var(--ring)] ring-2 ring-[var(--ring)]/15'
        )}
        aria-expanded={open}
        aria-haspopup="listbox"
      >
        <CheckIcon
          className={cx(
            'w-3.5 h-3.5 shrink-0 text-[var(--primary)] transition-opacity',
            selected && !loading ? 'opacity-100' : 'opacity-0'
          )}
        />
        <span className={cx('flex-1 min-w-0 truncate text-left', !selected && 'text-[var(--muted-foreground)]')}>
          {loading ? (
            '加载中…'
          ) : selected ? (
            <>
              <span className="font-mono font-medium">{selected.label}</span>
              {selected.description && (
                <span className="text-[var(--muted-foreground)] ml-1">{selected.description}</span>
              )}
            </>
          ) : (
            placeholder
          )}
        </span>
        <ChevronDownIcon
          className={cx(
            'w-3.5 h-3.5 shrink-0 text-[var(--muted-foreground)] transition-transform duration-200',
            open && 'rotate-180'
          )}
        />
      </button>

      {open && options.length > 0 && (
        <div
          role="listbox"
          className="absolute left-0 right-0 top-full mt-1.5 z-50 rounded-lg border border-[var(--border)] bg-[var(--card)] shadow-lg overflow-hidden modal-card-in"
        >
          <div className="max-h-[240px] overflow-y-auto py-1 [scrollbar-width:thin] [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-[var(--border)]">
            {options.map((opt) => {
              const active = opt.value === value;
              return (
                <button
                  key={String(opt.value)}
                  type="button"
                  role="option"
                  aria-selected={active}
                  onClick={() => {
                    onChange(opt.value);
                    setOpen(false);
                  }}
                  className={cx(
                    'w-full px-2.5 py-2 rounded-md text-left text-[12.5px] flex items-center gap-2 transition-colors',
                    active
                      ? 'bg-[var(--accent)]/50 text-[var(--foreground)]'
                      : 'hover:bg-[var(--muted)]/60 text-[var(--foreground)]'
                  )}
                >
                  <CheckIcon
                    className={cx(
                      'w-3.5 h-3.5 shrink-0 text-[var(--primary)] transition-opacity',
                      active ? 'opacity-100' : 'opacity-0'
                    )}
                  />
                  <span className="min-w-0 truncate">
                    <span className="font-mono font-medium">{opt.label}</span>
                    {opt.description && (
                      <span className="text-[var(--muted-foreground)] ml-1">{opt.description}</span>
                    )}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}