import { type ReactNode } from 'react';
import { XIcon } from './Icons';
import { ModalShell, type ModalShellSize } from './ModalShell';

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
  const shellSize: ModalShellSize = size === 'sm' ? 'sm' : 'md';

  return (
    <ModalShell onClose={onClose} size={shellSize}>
      {(requestClose) => (
        <>
          <div className="px-5 py-3.5 border-b border-[var(--border)] flex items-center justify-between">
            <h3 className="text-[14px] font-semibold tracking-tight">{title}</h3>
            <button
              onClick={requestClose}
              className="w-7 h-7 grid place-items-center rounded-md text-[var(--muted-foreground)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
            >
              <XIcon className="w-3.5 h-3.5" />
            </button>
          </div>
          <div className="p-5">{children}</div>
        </>
      )}
    </ModalShell>
  );
}