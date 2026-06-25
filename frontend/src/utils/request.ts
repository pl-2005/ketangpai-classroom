import axios, { AxiosRequestConfig, AxiosError, InternalAxiosRequestConfig } from 'axios';
import { message } from 'antd';
import { STATUS_CODE, STATUS_CODE_MESSAGE } from '../constants/statusCode';

// ============ 通用响应类型 ============
export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
}

export interface PageResponse<T = unknown> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ============ Axios 实例 ============
const instance = axios.create({
  baseURL: "http://localhost:3000",
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// ============ 请求拦截器 ============
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  }
);

// ============ 响应拦截器 ============
instance.interceptors.response.use(
  (response) => {
    const { data } = response;

    if (data.code === STATUS_CODE.SUCCESS) {
      return data.data;
    }

    message.error(data.message || '请求失败');

    if (data.code === STATUS_CODE.UNAUTHORIZED) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }

    return Promise.reject(new Error(data.message || '请求失败'));
  },
  (error: AxiosError) => {
    const status = error.response?.status;

    switch (status) {
      case STATUS_CODE.UNAUTHORIZED:
        message.error(STATUS_CODE_MESSAGE[STATUS_CODE.UNAUTHORIZED]);
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        window.location.href = '/login';
        break;
      case STATUS_CODE.FORBIDDEN:
        message.error(STATUS_CODE_MESSAGE[STATUS_CODE.FORBIDDEN]);
        break;
      case STATUS_CODE.NOT_FOUND:
        message.error(STATUS_CODE_MESSAGE[STATUS_CODE.NOT_FOUND]);
        break;
      case STATUS_CODE.CONFLICT:
        message.error(STATUS_CODE_MESSAGE[STATUS_CODE.CONFLICT]);
        break;
      case STATUS_CODE.INTERNAL_SERVER_ERROR:
        message.error(STATUS_CODE_MESSAGE[STATUS_CODE.INTERNAL_SERVER_ERROR]);
        break;
      default:
        message.error(error.message || '网络错误');
    }

    return Promise.reject(error);
  }
);

export default instance;
