import {
  DeleteOutlined,
  InboxOutlined,
  PlusOutlined,
  ReloadOutlined,
  TeamOutlined,
  UserAddOutlined,
} from '@ant-design/icons'
import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import {
  arrayMove,
  rectSortingStrategy,
  SortableContext,
  sortableKeyboardCoordinates,
} from '@dnd-kit/sortable'
import {
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Skeleton,
  Space,
  Tag,
  Typography,
} from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  createCourse,
  joinCourse,
  listCourses,
  listTrash,
  performCourseAction,
  performTrashAction,
  updateCourseOrder,
} from '@/api/courses'
import { errorMessage } from '@/api/client'
import { getCurrentUser } from '@/auth/session'
import SortableCourseCard from '@/components/SortableCourseCard'
import type { CourseCardModel, CourseTrashModel, CreateCourseInput } from '@/types/course'

type WorkspaceMode = 'active' | 'archived' | 'trash'

interface Props {
  mode: WorkspaceMode
}

const copy = {
  active: {
    eyebrow: 'COURSE WORKSPACE',
    title: '我的课程',
    description: '集中管理正在进行的课程，拖动卡片即可调整常用顺序。',
  },
  archived: {
    eyebrow: 'COURSE ARCHIVE',
    title: '归档课程',
    description: '暂时收起的课程会保留全部内容，可随时恢复。',
  },
  trash: {
    eyebrow: 'COURSE RECYCLE BIN',
    title: '课程回收站',
    description: '删除的课程可恢复；永久删除后课程关联数据无法找回。',
  },
}

export default function CourseWorkspacePage({ mode }: Props) {
  const navigate = useNavigate()
  const { message, modal } = App.useApp()
  const user = getCurrentUser()
  const [courses, setCourses] = useState<CourseCardModel[]>([])
  const [trash, setTrash] = useState<CourseTrashModel[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [joinOpen, setJoinOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [createForm] = Form.useForm<CreateCourseInput>()
  const [joinForm] = Form.useForm<{ courseCode: string }>()
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const load = useCallback(async () => {
    setLoading(true)
    try {
      if (mode === 'trash') {
        setTrash(await listTrash())
      } else {
        setCourses(await listCourses(mode === 'archived'))
      }
    } catch (error) {
      message.error(errorMessage(error, '课程加载失败'))
    } finally {
      setLoading(false)
    }
  }, [message, mode])

  useEffect(() => {
    void load()
  }, [load])

  async function submitCreate(values: CreateCourseInput) {
    setSubmitting(true)
    try {
      await createCourse(values)
      message.success('课程创建成功')
      setCreateOpen(false)
      createForm.resetFields()
      await load()
    } catch (error) {
      message.error(errorMessage(error, '课程创建失败'))
    } finally {
      setSubmitting(false)
    }
  }

  async function submitJoin(values: { courseCode: string }) {
    setSubmitting(true)
    try {
      await joinCourse(values.courseCode.trim().toUpperCase())
      message.success('已加入课程')
      setJoinOpen(false)
      joinForm.resetFields()
      await load()
    } catch (error) {
      message.error(errorMessage(error, '加入课程失败'))
    } finally {
      setSubmitting(false)
    }
  }

  function actionConfirmation(action: string) {
    switch (action) {
      case 'DELETE':
        return { title: '将课程移入回收站？', content: '课程将对所有成员隐藏，创建者可以在回收站恢复。' }
      case 'LEAVE':
        return { title: '确定退出课程？', content: '退出后需重新使用课程号加入。' }
      case 'ARCHIVE_FOR_ALL':
        return { title: '归档全部成员课程？', content: '所有成员会在归档区看到该课程。' }
      default:
        return null
    }
  }

  function handleCourseAction(course: CourseCardModel, action: string) {
    const execute = async () => {
      try {
        await performCourseAction(course.id, action)
        message.success('操作成功')
        await load()
      } catch (error) {
        message.error(errorMessage(error))
        throw error
      }
    }
    const confirmation = actionConfirmation(action)
    if (!confirmation) {
      void execute()
      return
    }
    modal.confirm({
      ...confirmation,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: { danger: action === 'DELETE' || action === 'LEAVE' },
      onOk: execute,
    })
  }

  function handleTrashAction(course: CourseTrashModel, action: 'RESTORE' | 'PURGE') {
    modal.confirm({
      title: action === 'PURGE' ? '永久删除该课程？' : '恢复该课程？',
      content:
        action === 'PURGE'
          ? '课程成员、作业、提交、资料与讨论数据将被永久删除，此操作无法撤销。'
          : '课程将按删除前的归档状态恢复。',
      okText: action === 'PURGE' ? '永久删除' : '恢复',
      cancelText: '取消',
      okButtonProps: { danger: action === 'PURGE' },
      onOk: async () => {
        try {
          await performTrashAction(course.id, action)
          message.success(action === 'PURGE' ? '课程已永久删除' : '课程已恢复')
          await load()
        } catch (error) {
          message.error(errorMessage(error))
          throw error
        }
      },
    })
  }

  async function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = courses.findIndex((course) => course.id === active.id)
    const newIndex = courses.findIndex((course) => course.id === over.id)
    if (oldIndex < 0 || newIndex < 0) return

    const previous = courses
    const ordered = arrayMove(courses, oldIndex, newIndex).map((course, index) => ({
      ...course,
      sortOrder: index,
    }))
    setCourses(ordered)
    try {
      await updateCourseOrder(ordered)
      message.success('课程顺序已保存')
    } catch (error) {
      setCourses(previous)
      message.error(errorMessage(error, '排序保存失败'))
    }
  }

  const pageCopy = copy[mode]
  const emptyDescription = mode === 'trash' ? '回收站为空' : mode === 'archived' ? '暂无归档课程' : '还没有课程'

  return (
    <div className="workspace-page">
      <section className="workspace-hero">
        <div>
          <Typography.Text className="eyebrow">{pageCopy.eyebrow}</Typography.Text>
          <Typography.Title>{pageCopy.title}</Typography.Title>
          <Typography.Paragraph>{pageCopy.description}</Typography.Paragraph>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          {mode === 'active' && (
            <>
              <Button icon={<UserAddOutlined />} onClick={() => setJoinOpen(true)}>
                加入课程
              </Button>
              {user?.role === 'TEACHER' && (
                <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                  创建课程
                </Button>
              )}
            </>
          )}
        </Space>
      </section>

      {loading ? (
        <div className="course-grid">
          {Array.from({ length: 4 }, (_, index) => (
            <Card key={index} className="course-card skeleton-card"><Skeleton active /></Card>
          ))}
        </div>
      ) : mode === 'trash' ? (
        trash.length === 0 ? (
          <Empty className="workspace-empty" description={emptyDescription} />
        ) : (
          <div className="course-grid">
            {trash.map((course) => (
              <Card key={course.id} className="trash-card">
                <Space direction="vertical" size={16} className="trash-card-content">
                  <div className="trash-icon"><DeleteOutlined /></div>
                  <div>
                    <Typography.Title level={4}>{course.name}</Typography.Title>
                    <Typography.Text type="secondary">课程号：{course.courseCode}</Typography.Text>
                  </div>
                  <Space wrap>
                    <Tag icon={<InboxOutlined />}>{course.status === 'ARCHIVED' ? '归档状态' : '活动状态'}</Tag>
                    <Tag>
                      删除于 {course.deletedAt ? dayjs(course.deletedAt).format('YYYY-MM-DD HH:mm') : '未知时间'}
                    </Tag>
                  </Space>
                  <Space className="trash-actions">
                    <Button onClick={() => handleTrashAction(course, 'RESTORE')}>恢复</Button>
                    <Button danger icon={<DeleteOutlined />} onClick={() => handleTrashAction(course, 'PURGE')}>
                      永久删除
                    </Button>
                  </Space>
                </Space>
              </Card>
            ))}
          </div>
        )
      ) : courses.length === 0 ? (
        <Empty
          className="workspace-empty"
          description={emptyDescription}
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        >
          {mode === 'active' && (
            <Button icon={<TeamOutlined />} onClick={() => setJoinOpen(true)}>加入第一门课程</Button>
          )}
        </Empty>
      ) : (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={courses.map((course) => course.id)} strategy={rectSortingStrategy}>
            <div className="course-grid">
              {courses.map((course) => (
                <SortableCourseCard
                  key={course.id}
                  course={course}
                  archived={mode === 'archived'}
                  onAction={handleCourseAction}
                  onOpen={(courseId) => navigate(`/courses/${courseId}`)}
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>
      )}

      <Modal
        title="创建课程"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form<CreateCourseInput> form={createForm} layout="vertical" onFinish={submitCreate} requiredMark={false}>
          <Form.Item name="name" label="课程名称" rules={[{ required: true, message: '请输入课程名称' }, { max: 100 }]}>
            <Input placeholder="例如：软件工程与计算 II" />
          </Form.Item>
          <Form.Item name="description" label="课程简介" rules={[{ max: 5000 }]}>
            <Input.TextArea rows={4} placeholder="介绍课程学期、班级或教学目标" />
          </Form.Item>
          <Form.Item name="coverUrl" label="封面地址" rules={[{ max: 255 }]}>
            <Input placeholder="可选，填写图片 URL" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="加入课程"
        open={joinOpen}
        onCancel={() => setJoinOpen(false)}
        onOk={() => joinForm.submit()}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form<{ courseCode: string }> form={joinForm} layout="vertical" onFinish={submitJoin} requiredMark={false}>
          <Form.Item
            name="courseCode"
            label="六位课程号"
            rules={[
              { required: true, message: '请输入课程号' },
              { pattern: /^[A-Za-z0-9]{6}$/, message: '课程号应为6位字母或数字' },
            ]}
          >
            <Input className="course-code-input" maxLength={6} placeholder="例如：SE2026" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
