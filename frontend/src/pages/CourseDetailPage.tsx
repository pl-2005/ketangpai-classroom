import {
  ArrowLeftOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CrownOutlined,
  FileTextOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  App,
  Avatar,
  Button,
  Card,
  Empty,
  List,
  Pagination,
  Select,
  Skeleton,
  Space,
  Statistic,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { listAssignments } from '@/api/assignments'
import { errorMessage } from '@/api/client'
import {
  getCourseDetail,
  listCourseMembers,
  updateCourseMemberRole,
} from '@/api/courses'
import type { PageResponse } from '@/types/api'
import type { AssignmentListItem, AssignmentStatus, SubmissionStatus } from '@/types/assignment'
import type { CourseDetailModel, CourseMemberModel, CourseMemberRole } from '@/types/course'

const assignmentStatusMeta = {
  DRAFT: { label: '草稿', color: 'orange' },
  PUBLISHED: { label: '进行中', color: 'blue' },
  CLOSED: { label: '已关闭', color: 'default' },
}

const submissionStatusMeta: Record<SubmissionStatus, { label: string; color: string }> = {
  DRAFT: { label: '未提交', color: 'default' },
  SUBMITTED: { label: '已提交', color: 'blue' },
  GRADED: { label: '已批阅', color: 'green' },
  RETURNED: { label: '已退回', color: 'orange' },
}

const memberRoleMeta: Record<CourseMemberRole, { label: string; color: string }> = {
  CREATOR: { label: '创建者', color: 'gold' },
  TEACHER: { label: '共管教师', color: 'blue' },
  STUDENT: { label: '学生', color: 'green' },
}

const emptyAssignmentPage: PageResponse<AssignmentListItem> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
}

export default function CourseDetailPage() {
  const { courseId: courseIdParam } = useParams()
  const courseId = Number(courseIdParam)
  const navigate = useNavigate()
  const { message, modal } = App.useApp()
  const [detail, setDetail] = useState<CourseDetailModel | null>(null)
  const [members, setMembers] = useState<CourseMemberModel[]>([])
  const [assignments, setAssignments] = useState(emptyAssignmentPage)
  const [assignmentStatus, setAssignmentStatus] = useState<AssignmentStatus | undefined>()
  const [loading, setLoading] = useState(true)
  const [assignmentLoading, setAssignmentLoading] = useState(false)
  const [roleUpdatingUserId, setRoleUpdatingUserId] = useState<number | null>(null)

  const loadAssignmentsPage = useCallback(async (
    page = 0,
    status?: AssignmentStatus,
  ) => {
    if (!Number.isInteger(courseId) || courseId <= 0) return
    setAssignmentLoading(true)
    try {
      setAssignments(await listAssignments(courseId, page, status))
    } catch (error) {
      message.error(errorMessage(error, '作业加载失败'))
    } finally {
      setAssignmentLoading(false)
    }
  }, [courseId, message])

  const loadMembers = useCallback(async () => {
    if (!Number.isInteger(courseId) || courseId <= 0) return
    setMembers(await listCourseMembers(courseId))
  }, [courseId])

  useEffect(() => {
    if (!Number.isInteger(courseId) || courseId <= 0) {
      navigate('/courses', { replace: true })
      return
    }
    const load = async () => {
      setLoading(true)
      try {
        const [course, courseMembers] = await Promise.all([
          getCourseDetail(courseId),
          listCourseMembers(courseId),
        ])
        setDetail(course)
        setMembers(courseMembers)
        await loadAssignmentsPage(0)
      } catch (error) {
        message.error(errorMessage(error, '课程详情加载失败'))
      } finally {
        setLoading(false)
      }
    }
    void load()
  }, [courseId, loadAssignmentsPage, message, navigate])

  function changeAssignmentStatus(value?: AssignmentStatus) {
    setAssignmentStatus(value)
    void loadAssignmentsPage(0, value)
  }

  function changeMemberRole(member: CourseMemberModel, role: 'TEACHER' | 'STUDENT') {
    const action = role === 'TEACHER' ? '授予共管教师权限' : '撤销共管教师权限'
    modal.confirm({
      title: `${action}？`,
      content: `目标成员：${member.realName || member.username}`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        setRoleUpdatingUserId(member.userId)
        try {
          await updateCourseMemberRole(courseId, member.userId, role)
          message.success('成员角色已更新')
          await loadMembers()
        } catch (error) {
          message.error(errorMessage(error, '成员角色更新失败'))
          throw error
        } finally {
          setRoleUpdatingUserId(null)
        }
      },
    })
  }

  if (loading || !detail) {
    return <Card className="detail-loading"><Skeleton active paragraph={{ rows: 8 }} /></Card>
  }

  const isTeacher = detail.currentUserRole !== 'STUDENT'
  const isCreator = detail.currentUserRole === 'CREATOR'
  const teachers = members.filter((member) => member.role !== 'STUDENT').length
  const students = members.filter((member) => member.role === 'STUDENT').length

  const assignmentTab = (
    <div className="detail-panel">
      <div className="panel-toolbar">
        <div>
          <Typography.Title level={4}>课程作业</Typography.Title>
          <Typography.Text type="secondary">
            {isTeacher ? '查看草稿、进行中和已关闭的作业。' : '查看已发布作业及自己的提交状态。'}
          </Typography.Text>
        </div>
        <Select<AssignmentStatus>
          allowClear
          value={assignmentStatus}
          placeholder="全部状态"
          style={{ width: 140 }}
          options={(isTeacher
            ? ['DRAFT', 'PUBLISHED', 'CLOSED']
            : ['PUBLISHED', 'CLOSED']
          ).map((status) => ({
            value: status as AssignmentStatus,
            label: assignmentStatusMeta[status as AssignmentStatus].label,
          }))}
          onChange={changeAssignmentStatus}
        />
      </div>

      <List
        className="assignment-list"
        loading={assignmentLoading}
        dataSource={assignments.content}
        locale={{ emptyText: <Empty description="暂无作业" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
        renderItem={(assignment) => {
          const status = assignmentStatusMeta[assignment.status]
          const submission = assignment.mySubmissionStatus
            ? submissionStatusMeta[assignment.mySubmissionStatus]
            : null
          const overdue = assignment.deadline
            ? dayjs(assignment.deadline).isBefore(dayjs()) && assignment.status === 'PUBLISHED'
            : false
          return (
            <List.Item className="assignment-row">
              <List.Item.Meta
                avatar={<div className={`assignment-icon assignment-${assignment.status.toLowerCase()}`}><FileTextOutlined /></div>}
                title={
                  <Space wrap>
                    <Typography.Text strong>{assignment.title}</Typography.Text>
                    <Tag color={status.color}>{status.label}</Tag>
                    {submission && <Tag color={submission.color}>{submission.label}</Tag>}
                  </Space>
                }
                description={
                  <Space wrap size={18}>
                    <span>满分 {assignment.maxScore}</span>
                    <span>
                      <CalendarOutlined />{' '}
                      {assignment.deadline
                        ? `${overdue ? '已截止' : '截止'} ${dayjs(assignment.deadline).format('YYYY-MM-DD HH:mm')}`
                        : '无截止时间'}
                    </span>
                    {assignment.allowResubmit && <span><CheckCircleOutlined /> 允许重新提交</span>}
                  </Space>
                }
              />
            </List.Item>
          )
        }}
      />
      {assignments.totalElements > assignments.size && (
        <Pagination
          className="detail-pagination"
          current={assignments.number + 1}
          pageSize={assignments.size}
          total={assignments.totalElements}
          showSizeChanger={false}
          onChange={(page) => void loadAssignmentsPage(page - 1, assignmentStatus)}
        />
      )}
    </div>
  )

  const memberTab = (
    <div className="detail-panel">
      <div className="panel-toolbar">
        <div>
          <Typography.Title level={4}>课程成员</Typography.Title>
          <Typography.Text type="secondary">
            创建者可以将已加入课程的教师账号设置为共管教师。
          </Typography.Text>
        </div>
        <Tag icon={<TeamOutlined />}>{members.length} 位成员</Tag>
      </div>
      <List
        className="member-list"
        dataSource={members}
        renderItem={(member) => {
          const role = memberRoleMeta[member.role]
          const canManage = isCreator && member.role !== 'CREATOR'
          return (
            <List.Item
              className="member-row"
              actions={canManage ? [
                <Select
                  key="role"
                  size="small"
                  value={member.role === 'TEACHER' ? 'TEACHER' : 'STUDENT'}
                  loading={roleUpdatingUserId === member.userId}
                  style={{ width: 120 }}
                  options={member.accountRole === 'TEACHER'
                    ? [
                        { value: 'TEACHER', label: '共管教师' },
                        { value: 'STUDENT', label: '普通成员' },
                      ]
                    : [{ value: 'STUDENT', label: '普通成员' }]}
                  onChange={(value: 'TEACHER' | 'STUDENT') => changeMemberRole(member, value)}
                />,
              ] : undefined}
            >
              <List.Item.Meta
                avatar={<Avatar size={42} src={member.avatarUrl} icon={<UserOutlined />} />}
                title={
                  <Space>
                    <Typography.Text strong>{member.realName || member.username}</Typography.Text>
                    <Tag color={role.color} icon={member.role === 'CREATOR' ? <CrownOutlined /> : undefined}>
                      {role.label}
                    </Tag>
                  </Space>
                }
                description={
                  <Space split={<span>·</span>}>
                    <span>@{member.username}</span>
                    <span>{member.accountRole === 'TEACHER' ? '教师账号' : '学生账号'}</span>
                    <span>加入于 {member.joinedAt ? dayjs(member.joinedAt).format('YYYY-MM-DD') : '未知'}</span>
                  </Space>
                }
              />
            </List.Item>
          )
        }}
      />
    </div>
  )

  return (
    <div className="course-detail-page">
      <Button className="detail-back" type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/courses')}>
        返回课程列表
      </Button>
      <section
        className="course-detail-hero"
        style={detail.coverUrl ? { backgroundImage: `linear-gradient(110deg, rgba(15, 42, 83, .94), rgba(35, 91, 175, .72)), url(${detail.coverUrl})` } : undefined}
      >
        <Space direction="vertical" size={12}>
          <Space wrap>
            <Tag color={detail.status === 'ACTIVE' ? 'green' : 'default'}>
              {detail.status === 'ACTIVE' ? '进行中' : '已归档'}
            </Tag>
            <Tag color="blue">{memberRoleMeta[detail.currentUserRole].label}</Tag>
          </Space>
          <Typography.Title>{detail.name}</Typography.Title>
          <Typography.Paragraph>{detail.description || '暂无课程简介'}</Typography.Paragraph>
          <Space wrap size={20} className="detail-hero-meta">
            <span>课程号 {detail.courseCode}</span>
            <span><TeamOutlined /> {detail.memberCount} 位成员</span>
            <span><ClockCircleOutlined /> 创建于 {detail.createTime ? dayjs(detail.createTime).format('YYYY-MM-DD') : '未知'}</span>
          </Space>
        </Space>
      </section>

      <div className="detail-stats">
        <Card><Statistic title="课程成员" value={members.length} prefix={<TeamOutlined />} /></Card>
        <Card><Statistic title="教师" value={teachers} prefix={<CrownOutlined />} /></Card>
        <Card><Statistic title="学生" value={students} prefix={<UserOutlined />} /></Card>
        <Card><Statistic title="作业" value={assignments.totalElements} prefix={<FileTextOutlined />} /></Card>
      </div>

      <Card className="detail-content-card">
        <Tabs
          defaultActiveKey="assignments"
          items={[
            { key: 'assignments', label: '作业', children: assignmentTab },
            { key: 'members', label: '成员', children: memberTab },
          ]}
        />
      </Card>
    </div>
  )
}
