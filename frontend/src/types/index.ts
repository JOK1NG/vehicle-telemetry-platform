export interface ApiResult<T = unknown> {
  code: number;
  message: string;
  data?: T;
}

export interface Vehicle {
  id: number;
  plateNo: string;
  vin?: string;
  model?: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface UserInfo {
  id: number;
  username: string;
  role: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  user: UserInfo;
}

export interface VehicleUpdateData {
  vehicleId: number;
  plateNo: string;
  lng: number;
  lat: number;
  speed: number;
  heading: number;
  battery: number;
  status: number;
}

export interface VehicleSnapshot extends VehicleUpdateData {
  lastTs: string;
}

export interface VehicleUpdateEnvelope {
  type: 'VEHICLE_UPDATE';
  timestamp: string;
  vehicles: VehicleUpdateData[];
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

export interface DashboardInsightResponse {
  summary: string;
  severity: string;
  findings: DashboardFinding[];
  recommendations: string[];
  latencyMs: number;
  timing?: DashboardInsightTiming;
}

export interface DashboardFinding {
  type?: string;
  description: string;
  detail?: string;
}

export interface DashboardInsightTiming {
  screenshotMs: number;
  contextMs: number;
  modelMs: number;
  parseMs: number;
  totalMs: number;
  imageInput: boolean;
}

export interface TelemetryInsightResponse {
  summary: string;
  severity: string;
  findings: string[];
  recommendations: string[];
  latencyMs: number;
}

export interface TelemetryInsightStreamEvent {
  type: 'delta' | 'final' | 'error';
  delta?: string;
  result?: TelemetryInsightResponse;
  error?: string;
  elapsedMs?: number;
}

// ==========================================
// 告警 (M2+)
// ==========================================

/** 严重级别：1=LOW 2=MEDIUM 3=HIGH 4=CRITICAL */
export type AlertLevel = 1 | 2 | 3 | 4;
export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/** 告警类型 (与 alert_rule.code 对齐) */
export type AlertType =
  | 'OVERSPEED'
  | 'LOW_BATTERY'
  | 'OFFLINE'
  | 'GEOFENCE_ENTER'
  | 'GEOFENCE_EXIT';

export interface AlertItem {
  id: number;
  vehicleId: number;
  plateNo: string;
  type: AlertType | string;
  /** 1=LOW 2=MEDIUM 3=HIGH 4=CRITICAL */
  level: AlertLevel;
  message: string;
  lng: number | null;
  lat: number | null;
  occurredAt: string;
  handled: boolean;
  ruleId: number | null;
  geofenceId: number | null;
}

export interface AlertEnvelope {
  type: 'ALERT';
  timestamp: string;
  alert: AlertItem;
}

export interface AlertRule {
  id: number;
  code: string;
  name: string;
  /** 1=LOW 2=MEDIUM 3=HIGH 4=CRITICAL */
  level: AlertLevel;
  metric: string;
  comparator: 'GT' | 'LT' | 'EQ';
  threshold: number;
  enabled: boolean;
  description?: string;
}

// ==========================================
// 地理围栏 (M2+)
// ==========================================

export type GeofenceType = 'CIRCLE' | 'POLYGON';

export interface LngLat {
  lng: number;
  lat: number;
}

export interface Geofence {
  id: number;
  name: string;
  type: GeofenceType;
  centerLng: number | null;
  centerLat: number | null;
  radiusM: number | null;
  polygon: LngLat[] | null;
  enabled: boolean;
  vehicleIds: number[];
  createdAt?: string;
  updatedAt?: string;
}

// ==========================================
// 轨迹回放 (M3+)
// ==========================================

export interface TrajectoryPoint {
  vehicleId: number;
  time: string;        // ISO 8601
  lng: number;
  lat: number;
  speed: number;
  heading: number;
  battery: number;
}
