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
