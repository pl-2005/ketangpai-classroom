import request from '../../utils/request';
import type { PageResponse } from '../../utils/request';

// ============ 类型定义 ============
export type SubmissionStatus = 'SUBMITTED' | 'GRADED' | 'RETURNED';

export interface SubmissionFile {
  id: number;
  fileName: string;
  fileUrl: string;
  fileSize: number;
}

export interface AiGradingDetail {
  dimension: string;
  score: number;
  maxScore: number;
  comment: string;
}

export interface AiGradingResult {
  score: number;
  comment: string;
  suggestions: string;
  detail: AiGradingDetail[];
  gradedAt: string;
}

export interface Submission {
  id: number;
  studentId: number;
  studentName: string;
  studentUsername: string;
  content?: string;
  status: SubmissionStatus;
  score?: number;
  version: number;
  files: SubmissionFile[];
  aiGradingResult?: AiGradingResult;
  submittedAt: string;
  gradedAt?: string;
}

export interface SubmitRequest {
  content?: string;
  fileIds?: number[];
}

export interface GradeRequest {
  score: number;
  teacherComment?: string;
}

export interface ReturnRequest {
  reason: string;
}

export interface GetSubmissionsParams {
  status?: SubmissionStatus;
  page?: number;
  size?: number;
}

// ============ API 接口 ============
export const submissionsApi = {
  submitAssignment: (assignmentId: number, data: SubmitRequest) => {
    return request.post(`/api/assignments/${assignmentId}/submit`, data);
  },

  getAssignmentSubmissions: (assignmentId: number, params?: GetSubmissionsParams) => {
    return request.get<PageResponse<Submission>>(`/api/assignments/${assignmentId}/submissions`, { params });
  },

  getSubmissionDetail: (submissionId: number) => {
    return request.get<Submission>(`/api/submissions/${submissionId}`);
  },

  gradeSubmission: (submissionId: number, data: GradeRequest) => {
    return request.put(`/api/submissions/${submissionId}/grade`, data);
  },

  returnSubmission: (submissionId: number, data: ReturnRequest) => {
    return request.post(`/api/submissions/${submissionId}/return`, data);
  },

  triggerAiGrading: (submissionId: number) => {
    return request.post<AiGradingResult>(`/api/submissions/${submissionId}/ai-grade`);
  },
};

export default submissionsApi;
