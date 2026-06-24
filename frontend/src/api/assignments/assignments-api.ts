import request from '../../utils/request';
import type { PageResponse } from '../../utils/request';

// ============ 类型定义 ============
export type AssignmentStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED';
export type GradingStyle = 'BALANCED' | 'STRICT' | 'LENIENT';

export interface FileAttachment {
  id: number;
  fileName: string;
  fileUrl: string;
  fileSize: number;
}

export interface AiGradingConfig {
  enabled: boolean;
  promptTemplate?: string;
  rubric?: RubricItem[];
  gradingStyle?: GradingStyle;
}

export interface RubricItem {
  dimension: string;
  weight: number;
  maxScore: number;
  criteria: string;
}

export interface AssignmentStats {
  totalStudents: number;
  submittedCount: number;
  gradedCount: number;
}

export interface Assignment {
  id: number;
  courseId: number;
  title: string;
  content?: string;
  status: AssignmentStatus;
  deadline?: string;
  maxScore: number;
  allowResubmit: boolean;
  attachments: FileAttachment[];
  aiGradingConfig?: AiGradingConfig;
  stats?: AssignmentStats;
  mySubmissionStatus?: 'SUBMITTED' | 'GRADED' | 'RETURNED' | null;
  createTime: string;
}

export interface CreateAssignmentRequest {
  courseId: number;
  title: string;
  content?: string;
  deadline?: string;
  maxScore?: number;
  allowResubmit?: boolean;
  attachmentIds?: number[];
}

export interface UpdateAssignmentRequest {
  title?: string;
  content?: string;
  deadline?: string;
  maxScore?: number;
  allowResubmit?: boolean;
}

export interface UpdateStatusRequest {
  status: 'PUBLISHED' | 'CLOSED';
}

export interface UrgeRequest {
  studentIds?: number[];
}

export interface GetAssignmentsParams {
  status?: AssignmentStatus;
  page?: number;
  size?: number;
}

// ============ API 接口 ============
export const assignmentsApi = {
  getCourseAssignments: (courseId: number, params?: GetAssignmentsParams) => {
    return request.get<PageResponse<Assignment>>(`/api/courses/${courseId}/assignments`, { params });
  },

  getAssignmentDetail: (assignmentId: number) => {
    return request.get<Assignment>(`/api/assignments/${assignmentId}`);
  },

  createAssignment: (data: CreateAssignmentRequest) => {
    return request.post<Assignment>('/api/assignments', data);
  },

  updateAssignment: (assignmentId: number, data: UpdateAssignmentRequest) => {
    return request.put(`/api/assignments/${assignmentId}`, data);
  },

  updateAssignmentStatus: (assignmentId: number, data: UpdateStatusRequest) => {
    return request.post(`/api/assignments/${assignmentId}/status`, data);
  },

  urgeAssignment: (assignmentId: number, data?: UrgeRequest) => {
    return request.post(`/api/assignments/${assignmentId}/urge`, data);
  },
};

export default assignmentsApi;
