import request from '../../utils/request';

// ============ 类型定义 ============
export interface RegisterRequest {
  username: string;
  password: string;
  email?: string;
  realName?: string;
  role: 'TEACHER' | 'STUDENT';
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface User {
  id: number;
  username: string;
  realName?: string;
  email?: string;
  role: 'TEACHER' | 'STUDENT';
  avatarUrl?: string;
}

export interface RegisterResponse {
  id: number;
  username: string;
  role: 'TEACHER' | 'STUDENT';
}

export interface LoginResponse {
  token: string;
  user: User;
}

// ============ API 接口 ============
export const authApi = {
  register: (data: RegisterRequest) => {
    return request.post<RegisterResponse>('/api/auth/register', data);
  },

  login: (data: LoginRequest) => {
    return request.post<LoginResponse>('/api/auth/login', data);
  },

  getCurrentUser: () => {
    return request.get<User>('/api/auth/me');
  },
};

export default authApi;
