import request from '../../utils/request';

// ============ 类型定义 ============
export interface UploadFileResponse {
  id: number;
  fileName: string;
  fileUrl: string;
  fileSize: number;
}

export interface FilePreviewResponse {
  previewUrl: string;
}

// ============ API 接口 ============
export const filesApi = {
  uploadFile: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request.post<UploadFileResponse>('/api/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  downloadFile: (fileId: number) => {
    return request.get(`/api/files/${fileId}/download`, { responseType: 'blob' });
  },

  previewFile: (fileId: number) => {
    return request.get<FilePreviewResponse>(`/api/files/${fileId}/preview`);
  },
};

export default filesApi;
