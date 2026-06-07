import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../stores/auth';

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截：自动携带 token
request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers = config.headers || {};
    (config.headers as Record<string, string>).Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截：统一错误处理 + token 失效跳转
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data;
    if (res && typeof res === 'object' && 'code' in res && res.code !== 0) {
      const msg = (res as { message?: string }).message || '请求失败';
      console.error('[API]', msg);
      return Promise.reject(new Error(msg));
    }
    return res;
  },
  (error: unknown) => {
    const err = error as {
      code?: string;
      message?: string;
      response?: { status?: number; data?: { message?: string } };
    };
    const status = err.response?.status;
    const msg =
      err.response?.data?.message ||
      (err.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : undefined) ||
      (err.message === 'Network Error' ? '网络错误' : err.message) ||
      '网络错误';

    if (status === 401) {
      useAuthStore.getState().logout();
      const here = window.location.pathname + window.location.search;
      window.location.href = `/login?redirect=${encodeURIComponent(here)}`;
    } else if (status === 403) {
      console.error('[API] 无权限执行此操作');
    }
    return Promise.reject(new Error(msg));
  }
);

export default request;
