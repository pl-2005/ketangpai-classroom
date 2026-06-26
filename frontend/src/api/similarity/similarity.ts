import request from '../../utils/request';

// ============ 类型定义 ============
export interface SimilaritySegment {
  textA: string;
  textB: string;
  score: number;
}

export interface SubmissionSummary {
  id: number;
  studentId: number;
  studentName: string;
}

export interface SimilarityPair {
  id: number;
  submissionA: SubmissionSummary;
  submissionB: SubmissionSummary;
  similarityScore: number;
  highlightedSegments: SimilaritySegment[];
}

export interface SimilarityReport {
  id: number;
  assignmentId: number;
  totalSubmissions: number;
  threshold: number;
  suspiciousCount: number;
  generatedAt: string;
}

export interface ReportDetailResponse {
  report: SimilarityReport;
  pairs: SimilarityPair[];
}

export interface AnalyzeRequest {
  threshold?: number;
}

// AnalyzeResponse 复用 SimilarityReport（后端直接返回 SimilarityReport 实体）
export type AnalyzeResponse = SimilarityReport;

// ============ API 接口 ============
export const similarityApi = {
  analyze: (assignmentId: number, data?: AnalyzeRequest) => {
    return request.post<AnalyzeResponse>(`/api/assignments/${assignmentId}/similarity/analyze`, data);
  },

  getReports: (assignmentId: number) => {
    return request.get<SimilarityReport[]>(`/api/assignments/${assignmentId}/similarity/reports`);
  },

  getReportDetail: (reportId: number) => {
    return request.get<ReportDetailResponse>(`/api/similarity/reports/${reportId}`);
  },
};

export default similarityApi;
