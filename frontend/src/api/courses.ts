import { http } from './client'
import type { ApiResult, PageResponse } from '@/types/api'
import type {
  CourseCardModel,
  CourseDetailModel,
  CourseMemberModel,
  CourseTrashModel,
  CreateCourseInput,
} from '@/types/course'

export async function listCourses(archived: boolean) {
  const response = await http.get<ApiResult<PageResponse<CourseCardModel>>>('/courses', {
    params: { archived, page: 0, size: 100 },
  })
  return response.data.data.content
}

export async function listTrash() {
  const response = await http.get<ApiResult<PageResponse<CourseTrashModel>>>('/courses/trash', {
    params: { page: 0, size: 100 },
  })
  return response.data.data.content
}

export async function getCourseDetail(courseId: number) {
  const response = await http.get<ApiResult<CourseDetailModel>>(`/courses/${courseId}`)
  return response.data.data
}

export async function listCourseMembers(courseId: number) {
  const response = await http.get<ApiResult<PageResponse<CourseMemberModel>>>(
    `/courses/${courseId}/members`,
    { params: { page: 0, size: 100 } },
  )
  return response.data.data.content
}

export async function updateCourseMemberRole(
  courseId: number,
  memberUserId: number,
  role: 'TEACHER' | 'STUDENT',
) {
  await http.put(`/courses/${courseId}/members/${memberUserId}/role`, { role })
}

export async function createCourse(input: CreateCourseInput) {
  await http.post('/courses', input)
}

export async function joinCourse(courseCode: string) {
  await http.post('/courses/join', { courseCode })
}

export async function performCourseAction(courseId: number, action: string) {
  await http.post(`/courses/${courseId}/action`, { action })
}

export async function updateCourseOrder(courses: CourseCardModel[]) {
  await http.put('/courses/order', {
    items: courses.map((course, index) => ({
      courseId: course.id,
      sortOrder: index,
    })),
  })
}

export async function performTrashAction(courseId: number, action: 'RESTORE' | 'PURGE') {
  await http.post(`/courses/${courseId}/trash/action`, { action })
}
