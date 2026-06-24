import request from '../../utils/request';

// ============ 类型定义 ============
export type DraftType = 'ASSIGNMENT' | 'MATERIAL' | 'TOPIC';

export interface Draft {
  id: number;
  type: DraftType;
  title: string;
  contentJson: string;
  createTime: string;
  updateTime: string;
}

export interface CreateDraftRequest {
  type: DraftType;
  title: string;
  contentJson: string;
}

export interface UpdateDraftRequest {
  title?: string;
  contentJson?: string;
}

export interface PublishDraftRequest {
  courseId: number;
}

// ============ API 接口 ============
export const draftsApi = {
  getDrafts: (params?: { type?: DraftType }) => {
    return request.get<Draft[]>('/api/drafts', { params });
  },

  createDraft: (data: CreateDraftRequest) => {
    return request.post<Draft>('/api/drafts', data);
  },

  updateDraft: (draftId: number, data: UpdateDraftRequest) => {
    return request.put(`/api/drafts/${draftId}`, data);
  },

  publishDraft: (draftId: number, data: PublishDraftRequest) => {
    return request.post(`/api/drafts/${draftId}/publish`, data);
  },

  deleteDraft: (draftId: number) => {
    return request.delete(`/api/drafts/${draftId}`);
  },
};

export default draftsApi;
