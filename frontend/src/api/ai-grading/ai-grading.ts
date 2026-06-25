import request from '../../utils/request';
import type { AiGradingConfig, AiGradingResult, GradingBatchTask } from './types';

// ============ API 接口 ============
export const aiGradingApi = {
  getAiGradingConfig: (assignmentId: number) => {
    return request.get<AiGradingConfig>(`/api/assignments/${assignmentId}/ai-grading-config`);
  },

  updateAiGradingConfig: (assignmentId: number, data: {
    enabled?: boolean;
    promptTemplate?: string;
    rubricJson?: string;
    gradingStyle?: string;
  }) => {
    return request.put<AiGradingConfig>(`/api/assignments/${assignmentId}/ai-grading-config`, data);
  },

  triggerAiGrading: (submissionId: number) => {
    return request.post<AiGradingResult>(`/api/submissions/${submissionId}/ai-grade`);
  },

  batchAiGrading: (assignmentId: number) => {
    return request.post<GradingBatchTask>(`/api/assignments/${assignmentId}/ai-grade-batch`);
  },

  getBatchTaskStatus: (assignmentId: number) => {
    return request.get<GradingBatchTask[]>(`/api/assignments/${assignmentId}/ai-grade-batch/status`);
  },

  getBatchTaskDetail: (taskId: number) => {
    return request.get<GradingBatchTask>(`/api/ai-grade-batch/${taskId}`);
  },
};

export default aiGradingApi;
