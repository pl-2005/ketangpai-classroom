import request from '../../utils/request';

// ============ 类型定义 ============
export type GradingStyle = 'BALANCED' | 'STRICT' | 'LENIENT';

export interface RubricItem {
  dimension: string;
  weight: number;
  maxScore: number;
  criteria: string;
}

export interface AiGradingConfig {
  enabled: boolean;
  promptTemplate?: string;
  rubric: RubricItem[];
  gradingStyle: GradingStyle;
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

export interface BatchAiGradingResponse {
  taskId: string;
  totalCount: number;
}

// ============ API 接口 ============
export const aiGradingApi = {
  getAiGradingConfig: (assignmentId: number) => {
    return request.get<AiGradingConfig>(`/api/assignments/${assignmentId}/ai-grading-config`);
  },

  updateAiGradingConfig: (assignmentId: number, data: AiGradingConfig) => {
    return request.put(`/api/assignments/${assignmentId}/ai-grading-config`, data);
  },

  triggerAiGrading: (submissionId: number) => {
    return request.post<AiGradingResult>(`/api/submissions/${submissionId}/ai-grade`);
  },

  batchAiGrading: (assignmentId: number) => {
    return request.post<BatchAiGradingResponse>(`/api/assignments/${assignmentId}/ai-grade-batch`);
  },
};

export default aiGradingApi;
