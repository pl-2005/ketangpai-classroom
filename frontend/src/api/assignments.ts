import { http } from './client'
import type { ApiResult, PageResponse } from '@/types/api'
import type { AssignmentListItem, AssignmentStatus } from '@/types/assignment'

export async function listAssignments(
  courseId: number,
  page = 0,
  status?: AssignmentStatus,
) {
  const response = await http.get<ApiResult<PageResponse<AssignmentListItem>>>(
    `/courses/${courseId}/assignments`,
    { params: { page, size: 20, status } },
  )
  return response.data.data
}
