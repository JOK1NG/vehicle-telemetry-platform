import request from './request'
import type { ApiResult, VehicleSnapshot } from '../types'

export const realtimeApi = {
  /** 获取所有在线车辆的实时快照 */
  async snapshot(): Promise<VehicleSnapshot[]> {
    const res = (await request.get('/api/vehicles/snapshot')) as unknown as ApiResult<VehicleSnapshot[]>
    return res.data ?? []
  },
}
