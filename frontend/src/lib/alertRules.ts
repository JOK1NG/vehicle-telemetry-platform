import type { AlertItem, AlertSeverity, AlertLevel, AlertType } from '../types';

/**
 * 告警规则 / 类型 映射表
 * - 中文标签
 * - 图标 (从 common/Icons 复用)
 * - 严重级别颜色（与 AiInsightPanel 配色体系保持一致）
 */

export const ALERT_RULE_LABELS: Record<string, { label: string; description: string }> = {
  OVERSPEED:       { label: '超速告警',     description: '速度超过阈值时触发' },
  LOW_BATTERY:     { label: '低电量告警',   description: '电量低于阈值时触发' },
  OFFLINE:         { label: '车辆离线',     description: '离线超过阈值时触发' },
  GEOFENCE_ENTER:  { label: '进入围栏',     description: '车辆进入地理围栏' },
  GEOFENCE_EXIT:   { label: '离开围栏',     description: '车辆离开地理围栏' },
};

export function getAlertLabel(type: string): string {
  return ALERT_RULE_LABELS[type]?.label ?? type;
}

export function getAlertDescription(type: string): string {
  return ALERT_RULE_LABELS[type]?.description ?? '';
}

/** 把数字 level (1-4) 映射为语义化 severity 字符串 */
export function levelToSeverity(level: AlertLevel | number | undefined | null): AlertSeverity {
  switch (level) {
    case 1: return 'LOW';
    case 2: return 'MEDIUM';
    case 3: return 'HIGH';
    case 4: return 'CRITICAL';
    default: return 'MEDIUM';
  }
}

/** 严重级别配色（与 AiInsightPanel/TelemetryInsightDialog 共享） */
export const ALERT_SEVERITY_COLOR: Record<AlertSeverity, string> = {
  LOW:      'text-[var(--chart-2)]',
  MEDIUM:   'text-amber-500',
  HIGH:     'text-orange-500',
  CRITICAL: 'text-[var(--destructive)]',
};

export const ALERT_SEVERITY_BG: Record<AlertSeverity, string> = {
  LOW:      'bg-[var(--chart-2)]/10 border-[var(--chart-2)]/25',
  MEDIUM:   'bg-amber-500/10 border-amber-500/25',
  HIGH:     'bg-orange-500/10 border-orange-500/25',
  CRITICAL: 'bg-[var(--destructive)]/10 border-[var(--destructive)]/25',
};

export function getSeverityColor(level: AlertLevel | number | undefined | null): string {
  return ALERT_SEVERITY_COLOR[levelToSeverity(level)];
}

export function getSeverityBg(level: AlertLevel | number | undefined | null): string {
  return ALERT_SEVERITY_BG[levelToSeverity(level)];
}

/** 告警类型对应的图标组件 key（在 AlertItem 渲染时查表） */
export const ALERT_TYPE_ICON_HINT: Record<string, string> = {
  OVERSPEED:      'gauge',
  LOW_BATTERY:    'battery',
  OFFLINE:        'wifi-off',
  GEOFENCE_ENTER: 'enter',
  GEOFENCE_EXIT:  'exit',
};

/** 工具：从 AlertItem 提取严重度（数字或字符串） */
export function getAlertSeverity(a: AlertItem): AlertSeverity {
  return levelToSeverity(a.level);
}
