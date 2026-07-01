import axios, { AxiosRequestConfig, AxiosError, InternalAxiosRequestConfig } from 'axios';
import { STATUS_CODE } from '../constants/statusCode';

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

    if (data.code === STATUS_CODE.UNAUTHORIZED) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }

    return Promise.reject(new Error(data.message || '请求失败'));
  },
  (error: AxiosError) => {
    const status = error.response?.status;
    // 优先使用后端返回的具体错误消息
    const serverMessage = (error.response?.data as { message?: string })?.message;

    if (status === STATUS_CODE.UNAUTHORIZED) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }

    // 将后端消息或状态信息附加到 error 上，由组件决定如何提示
    const enrichedError = new Error(serverMessage || error.message || '网络错误');
    return Promise.reject(enrichedError);
  }
);

export default instance;

// ============ SSE 流式请求（基于 fetch + ReadableStream） ============

export interface SSECallbacks {
  onStart?: (sessionId: string) => void;
  onChunk: (content: string) => void;
  onReferences?: (refs: unknown[]) => void;
  onDone: (messageId: number) => void;
  onError: (error: Error) => void;
}

/**
 * 通过 fetch + ReadableStream 发起 SSE POST 请求。
 * 返回 abort 函数用于中止流式生成。
 */
export function fetchSSE(
  url: string,
  body: object,
  callbacks: SSECallbacks,
): () => void {
  const token = localStorage.getItem('token');
  const controller = new AbortController();

  const fullUrl = url.startsWith('http') ? url : `http://localhost:3000${url}`;

  fetch(fullUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        const text = await response.text().catch(() => '');
        throw new Error(`HTTP ${response.status}: ${text}`);
      }
      if (!response.body) {
        throw new Error('浏览器不支持流式响应');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let completed = false; // 标记是否正常完成（收到 done 或 error 事件）

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // 解析 SSE 事件（以 \n\n 分隔）
        const parts = buffer.split('\n\n');
        // 最后一部分可能不完整，保留到下一次
        buffer = parts.pop() || '';

        for (const part of parts) {
          const event = parseSSEEvent(part);
          if (!event) continue;

          const d = event.data;
          switch (event.name) {
            case 'start':
              callbacks.onStart?.(String(d.sessionId ?? ''));
              break;
            case 'chunk':
              callbacks.onChunk(String(d.content ?? ''));
              break;
            case 'references':
              callbacks.onReferences?.((d.references as unknown[]) ?? []);
              break;
            case 'done':
              completed = true;
              callbacks.onDone(Number(d.messageId ?? 0));
              return; // 正常结束
            case 'error':
              completed = true;
              throw new Error(String(d.message ?? 'AI 服务不可用'));
          }
        }
      }
      // 流意外结束（连接关闭/超时）但未收到 done 或 error 事件
      if (!completed) {
        throw new Error('连接已断开，请检查网络或重试');
      }
    })
    .catch((err: Error) => {
      if (err.name === 'AbortError') return; // 用户主动中止
      callbacks.onError(err);
    });

  return () => controller.abort();
}

/** 解析单条 SSE 事件文本 */
function parseSSEEvent(text: string): { name: string; data: Record<string, unknown> } | null {
  const lines = text.split('\n');
  let name = '';
  let dataStr = '';

  for (const line of lines) {
    if (line.startsWith('event:')) {
      name = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataStr = line.slice(5).trim();
    }
  }

  if (!dataStr) return null;

  try {
    return { name: name || 'message', data: JSON.parse(dataStr) };
  } catch {
    return { name: name || 'message', data: { content: dataStr } };
  }
}
