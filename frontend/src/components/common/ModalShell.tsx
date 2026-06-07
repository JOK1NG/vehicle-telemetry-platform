import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { cx } from './utils';

const SIZE_CLASS = {
  sm: 'max-w-[400px]',
  md: 'max-w-[480px]',
  lg: 'max-w-[640px]',
  xl: 'max-w-md',
} as const;

export type ModalShellSize = keyof typeof SIZE_CLASS;

const CLOSE_ANIMATION_MS = 180;

export function ModalShell({
  onClose,
  onBeforeClose,
  size = 'md',
  className,
  closeOnBackdrop = true,
  children,
}: {
  onClose: () => void;
  onBeforeClose?: () => void;
  size?: ModalShellSize;
  className?: string;
  closeOnBackdrop?: boolean;
  children: ReactNode | ((requestClose: () => void) => ReactNode);
}) {
  const [closing, setClosing] = useState(false);

  const requestClose = () => {
    if (closing) return;
    onBeforeClose?.();
    setClosing(true);
    window.setTimeout(() => onClose(), CLOSE_ANIMATION_MS);
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') requestClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [onClose, closing]);

  const content = typeof children === 'function' ? children(requestClose) : children;

  return createPortal(
    <div
      className={cx(
        'fixed inset-0 z-50 grid place-items-center bg-black/60 p-4',
        closing ? 'modal-backdrop-out' : 'modal-backdrop-in'
      )}
      onClick={closeOnBackdrop ? requestClose : undefined}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className={cx(
          'w-full bg-[var(--card)] rounded-xl border border-[var(--border)] shadow-2xl',
          SIZE_CLASS[size],
          closing ? 'modal-card-out' : 'modal-card-in',
          className
        )}
      >
        {content}
      </div>
    </div>,
    document.body
  );
}