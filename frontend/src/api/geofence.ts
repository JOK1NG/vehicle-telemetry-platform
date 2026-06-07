import request from './client';
import type { Geofence, LngLat, GeofenceType, ApiResult } from '../types';

export const geofenceApi = {
  async list(): Promise<Geofence[]> {
    const res = await request.get('/api/geofences');
    return (res as unknown as ApiResult<Geofence[]>).data ?? [];
  },

  async get(id: number): Promise<Geofence> {
    const res = await request.get(`/api/geofences/${id}`);
    return (res as unknown as ApiResult<Geofence>).data!;
  },

  async create(input: {
    name: string;
    type: GeofenceType;
    centerLng?: number;
    centerLat?: number;
    radiusM?: number;
    polygon?: LngLat[];
    vehicleIds?: number[];
    enabled?: boolean;
  }): Promise<Geofence> {
    const res = await request.post('/api/geofences', input);
    return (res as unknown as ApiResult<Geofence>).data!;
  },

  async update(id: number, patch: Partial<Geofence>): Promise<Geofence> {
    const res = await request.put(`/api/geofences/${id}`, patch);
    return (res as unknown as ApiResult<Geofence>).data!;
  },

  async remove(id: number): Promise<void> {
    await request.delete(`/api/geofences/${id}`);
  },
};
