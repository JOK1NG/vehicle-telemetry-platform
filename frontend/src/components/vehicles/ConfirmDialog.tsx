import { Modal } from '../common/Modal';

export function ConfirmDialog({
  title,
  message,
  confirmText = '确认',
  cancelText = '取消',
  onConfirm,
  onCancel,
  destructive = true,
}: {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm: () => void;
  onCancel: () => void;
  destructive?: boolean;
}) {
  return (
    <Modal onClose={onCancel} title={title} size="sm">
      <p className="text-[13px] text-[var(--muted-foreground)] leading-relaxed">{message}</p>
      <div className="flex justify-end gap-2 mt-5">
        <button
          onClick={onCancel}
          className="h-9 px-3.5 rounded-md text-[12.5px] font-medium border border-[var(--border)] hover:bg-[var(--muted)]"
        >
          {cancelText}
        </button>
        <button
          onClick={onConfirm}
          className={`h-9 px-4 rounded-md text-[12.5px] font-medium text-white ${
            destructive
              ? 'bg-[var(--destructive)] hover:opacity-95'
              : 'bg-[var(--primary)] text-[var(--primary-foreground)] hover:opacity-95'
          }`}
        >
          {confirmText}
        </button>
      </div>
    </Modal>
  );
}
