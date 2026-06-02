import { useEffect, useState } from 'react';
import { Modal } from '../common/Modal';
import { CheckIcon } from '../common/Icons';
import { cx, fmtDate } from '../common/utils';
import { StatusBadge } from '../common/StatusBadge';
import type { Vehicle } from '../../types';

type Mode = 'create' | 'edit' | 'view';

export function VehicleDialog({
  mode,
  vehicle,
  onClose,
  onSubmit,
}: {
  mode: Mode;
  vehicle: Vehicle | null;
  onClose: () => void;
  onSubmit: (payload: { plateNo: string; vin?: string; model?: string }) => void;
}) {
  const [plateNo, setPlateNo] = useState(vehicle?.plateNo ?? '');
  const [vin, setVin] = useState(vehicle?.vin ?? '');
  const [model, setModel] = useState(vehicle?.model ?? '');
  const [errors, setErrors] = useState<{ plateNo?: string }>({});

  useEffect(() => {
    setPlateNo(vehicle?.plateNo ?? '');
    setVin(vehicle?.vin ?? '');
    setModel(vehicle?.model ?? '');
    setErrors({});
  }, [vehicle, mode]);

  const readOnly = mode === 'view';

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const errs: typeof errors = {};
    if (!plateNo.trim()) errs.plateNo = '车牌号不能为空';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    onSubmit({ plateNo: plateNo.trim(), vin: vin.trim() || undefined, model: model.trim() || undefined });
  };

  return (
    <Modal
      onClose={onClose}
      title={mode === 'create' ? '新增车辆' : mode === 'edit' ? '编辑车辆' : '车辆详情'}
    >
      <form onSubmit={submit} className="space-y-3.5">
        <div>
          <label className="text-[12px] font-medium text-[var(--muted-foreground)]">
            车牌号 <span className="text-[var(--destructive)]">*</span>
          </label>
          <input
            value={plateNo}
            onChange={(e) => {
              setPlateNo(e.target.value);
              if (errors.plateNo) setErrors({});
            }}
            disabled={readOnly}
            placeholder="如：沪A12345"
            maxLength={32}
            className={cx(
              'mt-1 w-full h-9 px-3 rounded-md border bg-[var(--background)] text-[13px] outline-none focus:border-[var(--ring)] focus:ring-2 focus:ring-[var(--ring)]/15',
              errors.plateNo ? 'border-[var(--destructive)]' : 'border-[var(--input)]',
              readOnly && 'opacity-70 cursor-default'
            )}
          />
          {errors.plateNo && (
            <div className="text-[11px] text-[var(--destructive)] mt-1">{errors.plateNo}</div>
          )}
        </div>
        <div>
          <label className="text-[12px] font-medium text-[var(--muted-foreground)]">VIN</label>
          <input
            value={vin}
            onChange={(e) => setVin(e.target.value)}
            disabled={readOnly}
            placeholder="车架号（可选）"
            maxLength={32}
            className="mt-1 w-full h-9 px-3 rounded-md border border-[var(--input)] bg-[var(--background)] text-[13px] outline-none focus:border-[var(--ring)] focus:ring-2 focus:ring-[var(--ring)]/15 disabled:opacity-70"
          />
        </div>
        <div>
          <label className="text-[12px] font-medium text-[var(--muted-foreground)]">车型</label>
          <input
            value={model}
            onChange={(e) => setModel(e.target.value)}
            disabled={readOnly}
            placeholder="如：Model 3（可选）"
            maxLength={64}
            className="mt-1 w-full h-9 px-3 rounded-md border border-[var(--input)] bg-[var(--background)] text-[13px] outline-none focus:border-[var(--ring)] focus:ring-2 focus:ring-[var(--ring)]/15 disabled:opacity-70"
          />
        </div>
        {readOnly && vehicle && (
          <div className="grid grid-cols-2 gap-2 pt-2 border-t border-[var(--border)]">
            <Detail label="ID" value={`#${vehicle.id}`} />
            <Detail label="状态">
              <StatusBadge status={vehicle.status} />
            </Detail>
            <Detail label="创建时间" value={fmtDate(vehicle.createdAt)} />
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="h-9 px-3.5 rounded-md text-[12.5px] font-medium border border-[var(--border)] hover:bg-[var(--muted)]"
          >
            {readOnly ? '关闭' : '取消'}
          </button>
          {!readOnly && (
            <button
              type="submit"
              className="h-9 px-4 rounded-md text-[12.5px] font-medium bg-[var(--primary)] text-[var(--primary-foreground)] hover:opacity-95 inline-flex items-center gap-1.5"
            >
              <CheckIcon className="w-3.5 h-3.5" />
              {mode === 'create' ? '新增' : '保存'}
            </button>
          )}
        </div>
      </form>
    </Modal>
  );
}

function Detail({ label, value, children }: { label: string; value?: string; children?: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10.5px] uppercase tracking-wider text-[var(--muted-foreground)]">
        {label}
      </div>
      <div className="text-[12.5px] font-medium mt-0.5 num">{children ?? value}</div>
    </div>
  );
}
