import { cx } from './utils';
import {
  ALERT_SEVERITY_BG,
  ALERT_SEVERITY_COLOR,
  levelToSeverity,
} from '../../lib/alertRules';
import type { AlertItem, AlertLevel, AlertSeverity } from '../../types';

interface SeverityBadgeProps {
  level: AlertLevel | number | undefined | null;
  /** 可选：覆盖显示文本（默认 LOW/MEDIUM/HIGH/CRITICAL） */
  label?: string;
  size?: 'sm' | 'md';
  className?: string;
}

export function SeverityBadge({ level, label, size = 'sm', className }: SeverityBadgeProps) {
  const sev: AlertSeverity = levelToSeverity(level);
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 rounded-full border font-medium',
        size === 'sm' ? 'h-5 px-1.5 text-[10.5px]' : 'h-6 px-2 text-[11.5px]',
        ALERT_SEVERITY_BG[sev],
        ALERT_SEVERITY_COLOR[sev],
        className
      )}
    >
      {label ?? sev}
    </span>
  );
}

/** 从告警对象直接渲染 */
export function AlertSeverityBadge({ alert, size }: { alert: AlertItem; size?: 'sm' | 'md' }) {
  return <SeverityBadge level={alert.level} size={size} />;
}
