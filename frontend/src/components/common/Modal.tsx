import { useEffect, type ReactNode } from 'react';
import { XIcon } from './Icons';

export function Modal({
  children,
  onClose,
  title,
  size = 'md',
}: {
  children: ReactNode;
  onClose: () => void;
  title: string;
  size?: 'sm' | 'md';
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/30 backdrop-blur-sm p-4 view-in"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className={`w-full bg-[var(--card)] rounded-xl border border-[var(--border)] shadow-2xl ${
          size === 'sm' ? 'max-w-[400px]' : 'max-w-[480px]'
        }`}
      >
        <div className="px-5 py-3.5 border-b border-[var(--border)] flex items-center justify-between">
          <h3 className="text-[14px] font-semibold tracking-tight">{title}</h3>
          <button
            onClick={onClose}
            className="w-7 h-7 grid place-items-center rounded-md text-[var(--muted-foreground)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
          >
            <XIcon className="w-3.5 h-3.5" />
          </button>
        </div>
        <div className="p-5">{children}</div>
      </div>
    </div>
  );
}
