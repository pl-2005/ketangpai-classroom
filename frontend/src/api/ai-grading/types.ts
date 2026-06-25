// ============ AI 批阅统一类型定义 ============
// 供 ai-grading.ts, submissions-api.ts, assignments-api.ts 共享

export type GradingStyle = 'STRICT' | 'BALANCED' | 'ENCOURAGING' | 'CONCISE';

export interface RubricItem {
  dimension: string;
  weight: number;
  maxScore: number;
  criteria: string;
}

export interface AiGradingConfig {
  id?: number;
  assignmentId?: number;
  enabled: boolean;
  promptTemplate?: string;
  rubricJson?: string;        // 后端存储的原始 JSON 字符串
  rubric?: RubricItem[];       // 前端解析后的数组
  gradingStyle: GradingStyle;
  createTime?: string;
  updateTime?: string;
}

export interface DimensionScore {
  dimension: string;
  score: number;
  maxScore: number;
  comment: string;
}

export interface AiGradingResult {
  id?: number;
  submissionId?: number;
  score?: number | null;
  comment: string;
  suggestions: string;
  detailJson?: string;          // 后端存储的原始 JSON 字符串
  dimensions?: DimensionScore[]; // 前端解析后的数组
  gradedAt: string;
}

export interface GradingBatchTask {
  id: number;
  assignmentId: number;
  teacherId: number;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'PARTIALLY_FAILED' | 'FAILED';
  totalCount: number;
  completedCount: number;
  failedCount: number;
  errorMessage?: string;
  createTime: string;
  updateTime: string;
}
