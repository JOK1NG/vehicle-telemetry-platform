import { useEffect } from 'react';
import { create } from 'zustand';

type ToastKind = 'success' | 'error' | 'info';
interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastState {
  toasts: ToastItem[];
  push: (kind: ToastKind, message: string) => void;
  remove: (id: number) => void;
}

const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (kind, message) => {
    const id = Date.now() + Math.random();
    set((s) => ({ toasts: [...s.toasts, { id, kind, message }] }));
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
    }, 3500);
  },
  remove: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

export const toast = {
  success: (m: string) => useToastStore.getState().push('success', m),
  error: (m: string) => useToastStore.getState().push('error', m),
  info: (m: string) => useToastStore.getState().push('info', m),
};

export function ToastContainer() {
  const toasts = useToastStore((s) => s.toasts);
  const remove = useToastStore((s) => s.remove);

  return (
    <div className="fixed top-4 right-4 z-[100] flex flex-col gap-2 max-w-sm">
      {toasts.map((t) => (
        <div
          key={t.id}
          onClick={() => remove(t.id)}
          className={`px-3.5 py-2.5 rounded-md text-[13px] shadow-lg border cursor-pointer view-in ${
            t.kind === 'success'
              ? 'bg-[var(--chart-2)]/10 border-[var(--chart-2)]/30 text-[var(--chart-2)]'
              : t.kind === 'error'
              ? 'bg-[var(--destructive)]/10 border-[var(--destructive)]/30 text-[var(--destructive)]'
              : 'bg-[var(--card)] border-[var(--border)] text-[var(--foreground)]'
          }`}
        >
          {t.message}
        </div>
      ))}
    </div>
  );
}
