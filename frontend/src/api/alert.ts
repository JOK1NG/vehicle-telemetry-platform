import request from './client';
import type { AlertItem, AlertLevel, PageResult, ApiResult } from '../types';

export const alertApi = {
  /** 分页历史告警 */
  async list(params?: {
    current?: number;
    size?: number;
    level?: AlertLevel;
    type?: string;
    handled?: boolean;
  }): Promise<PageResult<AlertItem>> {
    const res = await request.get('/api/alerts', { params });
    return (res as unknown as ApiResult<PageResult<AlertItem>>).data!;
  },

  /** 最近 N 条 */
  async latest(limit = 20): Promise<AlertItem[]> {
    const res = await request.get('/api/alerts/latest', { params: { limit } });
    return (res as unknown as ApiResult<AlertItem[]>).data ?? [];
  },

  /** 标记已处理 */
  async markHandled(id: number): Promise<void> {
    await request.patch(`/api/alerts/${id}/handle`);
  },

  /** 列出告警规则 */
  async listRules(): Promise<import('../types').AlertRule[]> {
    const res = await request.get('/api/alerts/rules');
    return (res as unknown as ApiResult<import('../types').AlertRule[]>).data ?? [];
  },

  /** 更新告警规则（Q4 可配置阈值） */
  async updateRule(id: number, patch: Partial<import('../types').AlertRule>): Promise<import('../types').AlertRule> {
    const res = await request.put(`/api/alerts/rules/${id}`, patch);
    return (res as unknown as ApiResult<import('../types').AlertRule>).data!;
  },
};
