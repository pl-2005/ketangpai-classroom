import { http } from './client'
import type { ApiResult } from '@/types/api'
import type { LoginResponse } from '@/types/course'

export async function login(username: string, password: string) {
  const response = await http.post<ApiResult<LoginResponse>>('/auth/login', {
    username,
    password,
  })
  return response.data.data
}
