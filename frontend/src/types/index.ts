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
