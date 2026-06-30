import request from '../../utils/request';
import type { PageResponse } from '../../utils/request';

// ============ 类型定义 ============
export type CourseStatus = 'ACTIVE' | 'ARCHIVED';
export type CourseMemberRole = 'CREATOR' | 'TEACHER' | 'STUDENT';
export type CourseAction = 'ARCHIVE' | 'UNARCHIVE' | 'ARCHIVE_FOR_ALL' | 'LEAVE' | 'DELETE';

export interface Course {
  id: number;
  name: string;
  courseCode: string;
  coverUrl?: string;
  status: CourseStatus;
  memberCount: number;
  role: CourseMemberRole;
  isArchived: boolean;
  sortOrder: number;
  createTime: string;
}

export interface CourseMember {
  id: number;
  userId: number;
  username: string;
  realName?: string;
  avatarUrl?: string;
  role: 'TEACHER' | 'STUDENT';
  joinedAt: string;
}

export interface CreateCourseRequest {
  name: string;
  description?: string;
  coverUrl?: string;
}

export interface UpdateCourseRequest {
  name?: string;
  description?: string;
  coverUrl?: string;
}

export interface JoinCourseRequest {
  courseCode: string;
}

export interface CourseActionRequest {
  action: CourseAction;
}

export interface UpdateMemberRoleRequest {
  role: 'TEACHER' | 'STUDENT';
}

export interface UpdateSortOrderRequest {
  courseIds: number[];
}

export interface GetCoursesParams {
  archived?: boolean;
  page?: number;
  size?: number;
}

export interface GetMembersParams {
  role?: 'TEACHER' | 'STUDENT';
  page?: number;
  size?: number;
}

// ============ API 接口 ============
export const courseApi = {
  getMyCourses: (params?: GetCoursesParams) => {
    return request.get<PageResponse<Course>>('/api/courses', { params });
  },

  createCourse: (data: CreateCourseRequest) => {
    return request.post<Course>('/api/courses', data);
  },

  joinCourse: (data: JoinCourseRequest) => {
    return request.post('/api/courses/join', data);
  },

  getCourseDetail: (courseId: number) => {
    return request.get<Course>(`/api/courses/${courseId}`);
  },

  getCourseMembers: (courseId: number, params?: GetMembersParams) => {
    return request.get<PageResponse<CourseMember>>(`/api/courses/${courseId}/members`, { params });
  },

  updateCourse: (courseId: number, data: UpdateCourseRequest) => {
    return request.put(`/api/courses/${courseId}`, data);
  },

  courseAction: (courseId: number, data: CourseActionRequest) => {
    return request.post(`/api/courses/${courseId}/action`, data);
  },

  updateMemberRole: (courseId: number, memberUserId: number, data: UpdateMemberRoleRequest) => {
    return request.put(`/api/courses/${courseId}/members/${memberUserId}/role`, data);
  },

  updateSortOrder: (data: UpdateSortOrderRequest) => {
    return request.put('/api/courses/sort-order', data);
  },
};

export default courseApi;
