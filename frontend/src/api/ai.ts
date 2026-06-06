import request from './client';
import type { ApiResult } from '../types';
import type { DashboardInsightResponse, TelemetryInsightResponse } from '../types';

export const aiApi = {
  async dashboardInsight(params: {
    vehicleId?: number;
    dashboardImageBase64?: string;
    timeRange?: string;
    summaryStats?: Record<string, unknown>;
  }): Promise<DashboardInsightResponse> {
    const res = await request.post('/api/ai/insights/dashboard', params, {
      timeout: 120000,
    });
    return (res as unknown as ApiResult<DashboardInsightResponse>).data!;
  },

  async telemetryInsight(params: {
    vehicleId: number;
    timeRange: { start: string; end: string };
    alerts?: string[];
  }): Promise<TelemetryInsightResponse> {
    const res = await request.post('/api/ai/insights/telemetry', params, {
      timeout: 120000,
    });
    return (res as unknown as ApiResult<TelemetryInsightResponse>).data!;
  },
};
