import request from './request'
import type { ApiResult, PageResult, Vehicle } from '../types'

export interface VehicleCreateRequest {
  plateNo: string
  vin?: string
  model?: string
}

export interface VehicleUpdateRequest {
  plateNo: string
  vin?: string
  model?: string
}

export const vehicleApi = {
  async list(current = 1, size = 10): Promise<PageResult<Vehicle>> {
    const res = await request.get('/api/vehicles', {
      params: { current, size },
    }) as unknown as ApiResult<PageResult<Vehicle>>
    return res.data!
  },

  async detail(id: number): Promise<Vehicle> {
    const res = await request.get(`/api/vehicles/${id}`) as unknown as ApiResult<Vehicle>
    return res.data!
  },

  async create(data: VehicleCreateRequest): Promise<Vehicle> {
    const res = await request.post('/api/vehicles', data) as unknown as ApiResult<Vehicle>
    return res.data!
  },

  async update(id: number, data: VehicleUpdateRequest): Promise<Vehicle> {
    const res = await request.put(`/api/vehicles/${id}`, data) as unknown as ApiResult<Vehicle>
    return res.data!
  },

  async remove(id: number): Promise<void> {
    await request.delete(`/api/vehicles/${id}`) as unknown as ApiResult<void>
  },
}
