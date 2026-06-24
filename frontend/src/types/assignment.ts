export type AssignmentStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
export type SubmissionStatus = 'DRAFT' | 'SUBMITTED' | 'GRADED' | 'RETURNED'

export interface AssignmentListItem {
  id: number
  courseId: number
  title: string
  status: AssignmentStatus
  deadline: string | null
  maxScore: number
  allowResubmit: boolean
  mySubmissionStatus: SubmissionStatus | null
  createTime: string | null
}
