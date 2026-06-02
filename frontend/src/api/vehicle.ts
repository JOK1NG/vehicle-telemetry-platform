import request from './client';
import type { ApiResult, PageResult, Vehicle } from '../types';

export interface VehicleCreateRequest {
  plateNo: string;
  vin?: string;
  model?: string;
}

export interface VehicleUpdateRequest {
  plateNo: string;
  vin?: string;
  model?: string;
}

export const vehicleApi = {
  async list(current = 1, size = 10): Promise<PageResult<Vehicle>> {
    const res = await request.get('/api/vehicles', { params: { current, size } });
    return (res as unknown as ApiResult<PageResult<Vehicle>>).data!;
  },

  async detail(id: number): Promise<Vehicle> {
    const res = await request.get(`/api/vehicles/${id}`);
    return (res as unknown as ApiResult<Vehicle>).data!;
  },

  async create(data: VehicleCreateRequest): Promise<Vehicle> {
    const res = await request.post('/api/vehicles', data);
    return (res as unknown as ApiResult<Vehicle>).data!;
  },

  async update(id: number, data: VehicleUpdateRequest): Promise<Vehicle> {
    const res = await request.put(`/api/vehicles/${id}`, data);
    return (res as unknown as ApiResult<Vehicle>).data!;
  },

  async remove(id: number): Promise<void> {
    await request.delete(`/api/vehicles/${id}`);
  },
};
