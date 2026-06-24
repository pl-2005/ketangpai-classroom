export { authApi } from './auth/auth-api';
export { userApi } from './user/user-api';
export { courseApi } from './courses/courses-api';
export { assignmentsApi } from './assignments/assignments-api';
export { submissionsApi } from './submissions/submissions-api';
export { materialsApi } from './materials/materials-api';
export { topicsApi } from './topics/topics-api';
export { draftsApi } from './drafts/drafts-api';
export { aiGradingApi } from './ai-grading/ai-grading';
export { similarityApi } from './similarity/similarity';
export { aiChatApi } from './ai-chat/ai-chat-api';
export { notificationsApi } from './notifications/notifications-api';
export { filesApi } from './files/files-api';

// 类型导出
export type { RegisterRequest, LoginRequest, User, LoginResponse } from './auth/auth-api';
export type { Course, CourseMember } from './courses/courses-api';
export type { Assignment } from './assignments/assignments-api';
export type { Submission } from './submissions/submissions-api';
