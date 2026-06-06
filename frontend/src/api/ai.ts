import request from './client';
import type { ApiResult } from '../types';
import type {
  DashboardInsightResponse,
  TelemetryInsightResponse,
  TelemetryInsightStreamEvent,
} from '../types';
import { useAuthStore } from '../stores/auth';

type TelemetryInsightParams = {
  vehicleId: number;
  timeRange: { start: string; end: string };
  alerts?: string[];
};

type TelemetryInsightStreamHandlers = {
  onDelta?: (delta: string) => void;
  onFinal?: (result: TelemetryInsightResponse) => void;
  onError?: (message: string) => void;
  signal?: AbortSignal;
};

function apiUrl(path: string): string {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
  return `${baseUrl}${path}`;
}

function redirectToLogin() {
  useAuthStore.getState().logout();
  const here = window.location.pathname + window.location.search;
  window.location.href = `/login?redirect=${encodeURIComponent(here)}`;
}

function dispatchStreamEvent(
  payload: TelemetryInsightStreamEvent,
  handlers: TelemetryInsightStreamHandlers
): TelemetryInsightResponse | null {
  if (payload.type === 'delta') {
    handlers.onDelta?.(payload.delta ?? '');
    return null;
  }
  if (payload.type === 'final' && payload.result) {
    handlers.onFinal?.(payload.result);
    return payload.result;
  }
  if (payload.type === 'error') {
    const message = payload.error || 'AI 流式诊断失败';
    handlers.onError?.(message);
    throw new Error(message);
  }
  return null;
}

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

  async telemetryInsight(params: TelemetryInsightParams): Promise<TelemetryInsightResponse> {
    const res = await request.post('/api/ai/insights/telemetry', params, {
      timeout: 120000,
    });
    return (res as unknown as ApiResult<TelemetryInsightResponse>).data!;
  },

  async telemetryInsightStream(
    params: TelemetryInsightParams,
    handlers: TelemetryInsightStreamHandlers = {}
  ): Promise<TelemetryInsightResponse> {
    const token = useAuthStore.getState().token;
    const response = await fetch(apiUrl('/api/ai/insights/telemetry/stream'), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(params),
      signal: handlers.signal,
    });

    if (response.status === 401) {
      redirectToLogin();
      throw new Error('登录已过期，请重新登录');
    }
    if (!response.ok) {
      throw new Error(`AI 流式诊断请求失败 (${response.status})`);
    }
    if (!response.body) {
      throw new Error('当前浏览器不支持流式响应');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let eventName = 'message';
    let dataLines: string[] = [];
    let finalResult: TelemetryInsightResponse | null = null;

    const flush = () => {
      if (dataLines.length === 0) {
        eventName = 'message';
        return;
      }
      const raw = dataLines.join('\n');
      dataLines = [];
      const payload = JSON.parse(raw) as TelemetryInsightStreamEvent;
      payload.type = payload.type || (eventName as TelemetryInsightStreamEvent['type']);
      const maybeResult = dispatchStreamEvent(payload, handlers);
      if (maybeResult) {
        finalResult = maybeResult;
      }
      eventName = 'message';
    };

    const consumeLine = (line: string) => {
      if (line === '') {
        flush();
      } else if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart());
      }
    };

    while (true) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? '';
      lines.forEach(consumeLine);
      if (done) {
        if (buffer.length > 0) {
          consumeLine(buffer);
          buffer = '';
        }
        flush();
        break;
      }
    }

    if (!finalResult) {
      throw new Error('AI 流式诊断未返回最终结果');
    }
    return finalResult;
  },
};
