import { useNavigate } from 'react-router-dom';
import { useAlertsStore } from '../../stores/alerts';
import { alertApi } from '../../api/alert';
import { cx, fmtTime } from '../common/utils';
import { ModalShell } from '../common/ModalShell';
import { BellIcon, MapMarkerIcon, RouteIcon, XIcon } from '../common/Icons';
import { SeverityBadge } from '../common/SeverityBadge';
import { getAlertLabel, getSeverityColor, getAlertSeverity } from '../../lib/alertRules';
import type { AlertItem } from '../../types';

interface Props {
  alert: AlertItem;
  onClick?: () => void;
  compact?: boolean;
}

export function AlertRow({ alert, onClick, compact }: Props) {
  const sev = getAlertSeverity(alert);
  const isUnread = !alert.handled;

  return (
    <button
      onClick={onClick}
      className={cx(
        'w-full text-left px-3 py-2.5 rounded-md border transition-colors',
        'flex items-start gap-2.5',
        compact ? '' : 'hover:bg-[var(--muted)]/40',
        isUnread ? 'bg-[var(--accent)]/20' : 'bg-transparent',
        'border-transparent hover:border-[var(--border)]'
      )}
    >
      <span
        className={cx(
          'w-1.5 h-1.5 rounded-full mt-1.5 shrink-0',
          isUnread ? 'bg-[var(--primary)]' : 'bg-transparent'
        )}
        aria-label={isUnread ? '未读' : '已读'}
      />
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2">
          <span className={cx('text-[12.5px] font-semibold truncate', getSeverityColor(alert.level))}>
            {getAlertLabel(alert.type)}
          </span>
          <SeverityBadge level={alert.level} size="sm" />
        </div>
        <div className="text-[11.5px] text-[var(--muted-foreground)] mt-0.5 line-clamp-2">
          {alert.message}
        </div>
        <div className="flex items-center gap-2 text-[10.5px] text-[var(--muted-foreground)] mt-1">
          <span className="font-mono">{alert.plateNo || `#${alert.vehicleId}`}</span>
          <span>·</span>
          <span className="num">{fmtTime(alert.occurredAt)}</span>
        </div>
      </div>
    </button>
  );
}

interface DetailProps {
  alert: AlertItem;
  onClose: () => void;
}

export function AlertDetailDialog({ alert, onClose }: DetailProps) {
  const navigate = useNavigate();
  const markHandled = useAlertsStore((s) => s.push); // no-op, just to ensure re-render

  const goMap = () => {
    if (alert.lng != null && alert.lat != null) {
      onClose();
      // 仅聚焦车辆，不带 alertId——避免大屏根据 URL 再次弹出告警详情遮罩
      navigate(`/dashboard?focusVehicleId=${alert.vehicleId}`);
    }
  };
  const goTrajectory = () => {
    onClose();
    navigate(`/trajectory?vehicleId=${alert.vehicleId}`);
  };

  return (
    <ModalShell onClose={onClose} size="xl">
      {(requestClose) => (
        <>
        <div className="px-4 py-3 border-b border-[var(--border)] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <BellIcon className="w-4 h-4 text-[var(--primary)]" />
            <span className="text-[14px] font-semibold">告警详情</span>
            <SeverityBadge level={alert.level} />
          </div>
          <button onClick={requestClose} className="w-7 h-7 grid place-items-center rounded-md hover:bg-[var(--muted)]">
            <XIcon className="w-4 h-4" />
          </button>
        </div>

        <div className="px-4 py-4 space-y-3">
          <div>
            <div className="text-[11px] text-[var(--muted-foreground)]">类型</div>
            <div className={cx('text-[14px] font-semibold', getSeverityColor(alert.level))}>
              {getAlertLabel(alert.type)}
            </div>
          </div>

          <div>
            <div className="text-[11px] text-[var(--muted-foreground)]">详情</div>
            <div className="text-[13px] mt-0.5">{alert.message}</div>
          </div>

          <div className="grid grid-cols-2 gap-3 text-[12px]">
            <div>
              <div className="text-[11px] text-[var(--muted-foreground)]">车辆</div>
              <div className="font-mono">{alert.plateNo || `#${alert.vehicleId}`}</div>
            </div>
            <div>
              <div className="text-[11px] text-[var(--muted-foreground)]">发生时间</div>
              <div className="num">{fmtTime(alert.occurredAt)}</div>
            </div>
            {alert.geofenceId != null && (
              <div>
                <div className="text-[11px] text-[var(--muted-foreground)]">围栏 ID</div>
                <div className="num">#{alert.geofenceId}</div>
              </div>
            )}
            {alert.ruleId != null && (
              <div>
                <div className="text-[11px] text-[var(--muted-foreground)]">规则 ID</div>
                <div className="num">#{alert.ruleId}</div>
              </div>
            )}
          </div>

          {alert.lng != null && alert.lat != null && (
            <div>
              <div className="text-[11px] text-[var(--muted-foreground)]">位置</div>
              <div className="text-[11.5px] num">
                {alert.lng.toFixed(5)}, {alert.lat.toFixed(5)}
              </div>
            </div>
          )}
        </div>

        <div className="px-4 py-3 border-t border-[var(--border)] flex items-center justify-end gap-2">
          {!alert.handled && (
            <button
              onClick={async () => {
                try {
                  await alertApi.markHandled(alert.id);
                  useAlertsStore.getState().clear(); // 触发刷新（实际是 clear+re-push 太重，这里简化只重置该条 handled）
                } catch {}
              }}
              className="h-8 px-3 rounded-md text-[12px] font-medium border border-[var(--border)] bg-[var(--card)] hover:bg-[var(--muted)]"
            >
              标记已读
            </button>
          )}
          <button
            onClick={goTrajectory}
            className="h-8 px-3 rounded-md text-[12px] font-medium border border-[var(--border)] bg-[var(--card)] hover:bg-[var(--muted)] inline-flex items-center gap-1.5"
          >
            <RouteIcon className="w-3.5 h-3.5" /> 查看轨迹
          </button>
          {alert.lng != null && alert.lat != null && (
            <button
              onClick={goMap}
              className="h-8 px-3 rounded-md text-[12px] font-medium inline-flex items-center gap-1.5 bg-[var(--primary)] text-[var(--primary-foreground)] hover:opacity-95"
            >
              <MapMarkerIcon className="w-3.5 h-3.5" /> 定位到地图
            </button>
          )}
        </div>
        </>
      )}
    </ModalShell>
  );
}
