import request from './client';
import type { ApiResult, VehicleSnapshot } from '../types';

export const realtimeApi = {
  async snapshot(): Promise<VehicleSnapshot[]> {
    const res = await request.get('/api/vehicles/snapshot');
    return (res as unknown as ApiResult<VehicleSnapshot[]>).data ?? [];
  },
};
