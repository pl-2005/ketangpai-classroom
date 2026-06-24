import request from '../../utils/request';
import type { PageResponse } from '../../utils/request';

// ============ 类型定义 ============
export type NotificationType = 
  | 'ASSIGNMENT_PUBLISHED' 
  | 'ASSIGNMENT_CLOSED' 
  | 'ASSIGNMENT_URGE' 
  | 'SUBMISSION_GRADED' 
  | 'SUBMISSION_RETURNED' 
  | 'TOPIC_REPLY';

export interface Notification {
  id: number;
  type: NotificationType;
  title: string;
  content: string;
  isRead: boolean;
  relatedId?: number;
  createTime: string;
}

export interface UnreadCountResponse {
  count: number;
}

export interface GetNotificationsParams {
  page?: number;
  size?: number;
  type?: NotificationType;
}

// ============ API 接口 ============
export const notificationsApi = {
  getNotifications: (params?: GetNotificationsParams) => {
    return request.get<PageResponse<Notification>>('/api/notifications', { params });
  },

  getUnreadCount: () => {
    return request.get<UnreadCountResponse>('/api/notifications/unread-count');
  },

  markAsRead: (notificationId: number) => {
    return request.put(`/api/notifications/${notificationId}/read`);
  },

  markAllAsRead: () => {
    return request.put('/api/notifications/read-all');
  },

  deleteNotification: (notificationId: number) => {
    return request.delete(`/api/notifications/${notificationId}`);
  },
};

export default notificationsApi;
