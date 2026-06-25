import request from '../../utils/request';
import type { PageResponse } from '../../utils/request';

// ============ 类型定义 ============
export type TopicStatus = 'NORMAL' | 'PINNED' | 'LOCKED';

export interface Topic {
  id: number;
  title: string;
  content: string;
  status: TopicStatus;
  authorId: number;
  authorName: string;
  isAnonymous: boolean;
  discussionEnabled: boolean;
  replyCount: number;
  createTime: string;
}

export interface TopicReply {
  id: number;
  topicId: number;
  content: string;
  authorId: number;
  authorName: string;
  isAnonymous: boolean;
  parentId?: number;
  path: string;
  createTime: string;
}

export interface TopicDetailResponse {
  topic: Topic;
  replies: PageResponse<TopicReply>;
}

export interface CreateTopicRequest {
  courseId: number;
  title: string;
  content: string;
  isAnonymous?: boolean;
}

export interface ReplyRequest {
  content: string;
  isAnonymous?: boolean;
  parentId?: number;
}

export interface UpdateTopicStatusRequest {
  status: TopicStatus;
}

export interface UpdateTopicRequest {
  title?: string;
  content?: string;
  isAnonymous?: boolean;
}

// ============ API 接口 ============
export const topicsApi = {
  getCourseTopics: (courseId: number) => {
    return request.get<PageResponse<Topic>>(`/api/courses/${courseId}/topics`);
  },

  getTopicDetail: (topicId: number, params?: { page?: number; size?: number }) => {
    return request.get<TopicDetailResponse>(`/api/topics/${topicId}`, { params });
  },

  createTopic: (data: CreateTopicRequest) => {
    return request.post<Topic>('/api/topics', data);
  },

  replyTopic: (topicId: number, data: ReplyRequest) => {
    return request.post<TopicReply>(`/api/topics/${topicId}/replies`, data);
  },

  updateTopicStatus: (topicId: number, data: UpdateTopicStatusRequest) => {
    return request.post(`/api/topics/${topicId}/status`, data);
  },

  updateTopic: (topicId: number, data: UpdateTopicRequest) => {
    return request.put(`/api/topics/${topicId}`, data);
  },

  deleteTopic: (topicId: number) => {
    return request.delete(`/api/topics/${topicId}`);
  },

  deleteReply: (replyId: number) => {
    return request.delete(`/api/topics/replies/${replyId}`);
  },

  toggleDiscussion: (topicId: number) => {
    return request.put<Topic>(`/api/topics/${topicId}/discussion`);
  },
};

export default topicsApi;
