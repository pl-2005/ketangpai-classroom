import request from '../../utils/request';

// ============ 类型定义 ============
export type ChatRole = 'USER' | 'ASSISTANT';

export interface ChatReference {
  chunkId: number;
  sourceName: string;
  sourceType: string;
  excerpt: string;
}

export interface ChatMessage {
  id: number;
  userId: number;
  courseId: number;
  sessionId: string;
  role: ChatRole;
  content: string;
  referencesJson?: string;
  references?: ChatReference[];
  createTime: string;
}

export interface ChatSession {
  sessionId: string;
  title: string;
  lastMessage?: string;
  lastTime?: string;
}

export interface SendMessageRequest {
  sessionId: string;
  content: string;
}

// ============ API 接口 ============
export const aiChatApi = {
  createSession: (courseId: number) => {
    return request.post<{ sessionId: string }>(`/api/courses/${courseId}/ai-chat/sessions`);
  },

  getSessions: (courseId: number) => {
    return request.get<ChatSession[]>(`/api/courses/${courseId}/ai-chat/sessions`);
  },

  sendMessage: (courseId: number, data: SendMessageRequest) => {
    return request.post<ChatMessage>(`/api/courses/${courseId}/ai-chat`, data);
  },

  getSessionHistory: (courseId: number, sessionId: string, params?: { page?: number; size?: number }) => {
    return request.get<ChatMessage[]>(`/api/courses/${courseId}/ai-chat/sessions/${sessionId}`, { params });
  },

  deleteSession: (courseId: number, sessionId: string) => {
    return request.delete(`/api/courses/${courseId}/ai-chat/sessions/${sessionId}`);
  },

  /** 重建课程知识库（教师权限） */
  rebuildKnowledge: (courseId: number) => {
    return request.post<{ message: string }>(`/api/courses/${courseId}/ai-chat/knowledge/rebuild`);
  },
};

export default aiChatApi;
