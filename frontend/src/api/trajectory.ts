import request from './client';
import type { TrajectoryPoint, ApiResult } from '../types';

export const trajectoryApi = {
  /**
   * 查询指定车辆在时间窗口内的轨迹
   * @param maxPoints 最大返回点数（默认 2000，最大 5000）
   */
  async byVehicle(vehicleId: number, start: string, end: string, maxPoints = 2000): Promise<TrajectoryPoint[]> {
    const res = await request.get(`/api/vehicles/${vehicleId}/trajectory`, {
      params: { start, end, maxPoints },
    });
    return (res as unknown as ApiResult<TrajectoryPoint[]>).data ?? [];
  },
};
