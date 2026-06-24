import axios, { AxiosError } from 'axios'
import { clearSession, getToken } from '@/auth/session'
import type { ApiResult } from '@/types/api'

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15_000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResult<unknown>>) => {
    if (error.response?.status === 401) {
      clearSession()
      if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    }
    return Promise.reject(error)
  },
)

export function errorMessage(error: unknown, fallback = '操作失败') {
  if (axios.isAxiosError<ApiResult<unknown>>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}
