import request from '../../utils/request';

// ============ 类型定义 ============
export type MaterialType = 'FILE' | 'LINK';

export interface Material {
  id: number;
  title: string;
  type: MaterialType;
  fileUrl?: string;
  fileSize?: number;
  linkUrl?: string;
  sortOrder: number;
}

export interface MaterialFolder {
  id: number;
  name: string;
  parentId?: number;
  sortOrder: number;
  children?: MaterialFolder[];
  materials?: Material[];
}

export interface MaterialTreeResponse {
  folders: MaterialFolder[];
}

export interface CreateFolderRequest {
  courseId: number;
  parentId?: number;
  name: string;
}

export interface CreateMaterialRequest {
  courseId: number;
  folderId?: number;
  type: MaterialType;
  title: string;
  fileUrl?: string;
  fileSize?: number;
  linkUrl?: string;
  fileId?: number;
}

export interface MoveMaterialRequest {
  targetFolderId?: number;
}

export interface UpdateMaterialRequest {
  title?: string;
  sortOrder?: number;
}

// ============ API 接口 ============
export const materialsApi = {
  getMaterialTree: (courseId: number) => {
    return request.get<MaterialTreeResponse>(`/api/courses/${courseId}/materials/tree`);
  },

  createFolder: (data: CreateFolderRequest) => {
    return request.post('/api/materials/folders', data);
  },

  createMaterial: (data: CreateMaterialRequest) => {
    return request.post('/api/materials', data);
  },

  moveMaterial: (materialId: number, data: MoveMaterialRequest) => {
    return request.put(`/api/materials/${materialId}/move`, data);
  },

  updateMaterial: (materialId: number, data: UpdateMaterialRequest) => {
    return request.put(`/api/materials/${materialId}`, data);
  },

  deleteMaterial: (materialId: number) => {
    return request.delete(`/api/materials/${materialId}`);
  },

  updateFolder: (folderId: number, data: { name: string }) => {
    return request.put(`/api/materials/folders/${folderId}`, data);
  },

  deleteFolder: (folderId: number) => {
    return request.delete(`/api/materials/folders/${folderId}`);
  },

  getMaterialDownloadUrl: (materialId: number) => {
    return request.get<{ url: string }>(`/api/materials/${materialId}/download`);
  },
};

export default materialsApi;
