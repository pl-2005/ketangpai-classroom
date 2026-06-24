import request from '../../utils/request';

// ============ 类型定义 ============
export interface UpdateProfileRequest {
  realName?: string;
  email?: string;
}

export interface UpdatePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

export interface UploadAvatarResponse {
  avatarUrl: string;
}

// ============ API 接口 ============
export const userApi = {
  updateProfile: (data: UpdateProfileRequest) => {
    return request.put('/api/user/profile', data);
  },

  updatePassword: (data: UpdatePasswordRequest) => {
    return request.put('/api/user/password', data);
  },

  uploadAvatar: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return request.post<UploadAvatarResponse>('/api/user/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

export default userApi;
