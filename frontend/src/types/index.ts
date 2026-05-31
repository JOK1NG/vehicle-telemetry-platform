export interface ApiResult<T = unknown> {
  code: number
  message: string
  data?: T
}

export interface Vehicle {
  id: number
  plateNo: string
  vin?: string
  model?: string
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface UserInfo {
  id: number
  username: string
  role: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  user: UserInfo
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}
