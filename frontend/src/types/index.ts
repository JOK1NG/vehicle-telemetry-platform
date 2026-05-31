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
