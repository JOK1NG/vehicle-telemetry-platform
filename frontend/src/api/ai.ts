import request from './client';
import type { ApiResult } from '../types';
import type { DashboardInsightResponse } from '../types';

export const aiApi = {
  async dashboardInsight(params: {
    vehicleId?: number;
    dashboardImageBase64?: string;
    timeRange?: string;
    summaryStats?: Record<string, unknown>;
  }): Promise<DashboardInsightResponse> {
    const res = await request.post('/api/ai/insights/dashboard', params);
    return (res as unknown as ApiResult<DashboardInsightResponse>).data!;
  },
};
