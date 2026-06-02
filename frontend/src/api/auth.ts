import request from './client';
import type { ApiResult, LoginResponse, UserInfo } from '../types';

export interface LoginRequest {
  username: string;
  password: string;
}

export const authApi = {
  async login(data: LoginRequest): Promise<LoginResponse> {
    const res = await request.post('/api/auth/login', data);
    return (res as unknown as ApiResult<LoginResponse>).data!;
  },

  async me(): Promise<UserInfo> {
    const res = await request.get('/api/auth/me');
    return (res as unknown as ApiResult<UserInfo>).data!;
  },
};
