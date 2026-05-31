import request from './request'
import type { ApiResult, LoginResponse, UserInfo } from '../types'

export interface LoginRequest {
  username: string
  password: string
}

export const authApi = {
  async login(data: LoginRequest): Promise<LoginResponse> {
    const res = await request.post('/api/auth/login', data) as unknown as ApiResult<LoginResponse>
    return res.data!
  },

  async me(): Promise<UserInfo> {
    const res = await request.get('/api/auth/me') as unknown as ApiResult<UserInfo>
    return res.data!
  },
}
