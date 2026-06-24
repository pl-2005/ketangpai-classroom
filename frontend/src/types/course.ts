export type UserRole = 'TEACHER' | 'STUDENT'
export type CourseMemberRole = 'CREATOR' | 'TEACHER' | 'STUDENT'
export type CourseStatus = 'ACTIVE' | 'ARCHIVED'

export interface CurrentUser {
  id: number
  username: string
  realName: string | null
  email: string | null
  role: UserRole
  avatarUrl: string | null
}

export interface CourseCardModel {
  id: number
  name: string
  courseCode: string
  coverUrl: string | null
  status: CourseStatus
  memberCount: number
  role: CourseMemberRole
  isArchived: boolean
  sortOrder: number
  createTime: string | null
}

export interface CourseDetailModel {
  id: number
  name: string
  description: string | null
  courseCode: string
  coverUrl: string | null
  status: CourseStatus
  creatorId: number
  currentUserRole: CourseMemberRole
  memberCount: number
  createTime: string | null
  updateTime: string | null
}

export interface CourseMemberModel {
  id: number
  userId: number
  username: string
  realName: string | null
  avatarUrl: string | null
  accountRole: UserRole
  role: CourseMemberRole
  joinedAt: string | null
}

export interface CourseTrashModel {
  id: number
  name: string
  courseCode: string
  coverUrl: string | null
  status: CourseStatus
  deletedAt: string | null
}

export interface LoginResponse {
  token: string
  user: CurrentUser
}

export interface CreateCourseInput {
  name: string
  description?: string
  coverUrl?: string
}
