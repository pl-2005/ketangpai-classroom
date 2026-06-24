import type { CurrentUser, LoginResponse } from '@/types/course'

const TOKEN_KEY = 'ketangpai_token'
const USER_KEY = 'ketangpai_user'

export function saveSession(session: LoginResponse) {
  localStorage.setItem(TOKEN_KEY, session.token)
  localStorage.setItem(USER_KEY, JSON.stringify(session.user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getCurrentUser(): CurrentUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as CurrentUser
  } catch {
    clearSession()
    return null
  }
}
