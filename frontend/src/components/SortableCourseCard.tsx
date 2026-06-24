import {
  DeleteOutlined,
  HolderOutlined,
  InboxOutlined,
  MoreOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useSortable } from '@dnd-kit/sortable'
import { Button, Card, Dropdown, Space, Tag, Typography, type MenuProps } from 'antd'
import type { CSSProperties } from 'react'
import type { CourseCardModel } from '@/types/course'

interface Props {
  course: CourseCardModel
  archived: boolean
  onAction: (course: CourseCardModel, action: string) => void
  onOpen: (courseId: number) => void
}

const roleLabel = {
  CREATOR: '创建者',
  TEACHER: '共管教师',
  STUDENT: '学生',
}

export default function SortableCourseCard({ course, archived, onAction, onOpen }: Props) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: course.id,
  })
  const style: CSSProperties = {
    transform: transform
      ? `translate3d(${transform.x}px, ${transform.y}px, 0) scale(${transform.scaleX}, ${transform.scaleY})`
      : undefined,
    transition,
    opacity: isDragging ? 0.65 : 1,
    zIndex: isDragging ? 2 : undefined,
  }

  const items: MenuProps['items'] = archived
    ? [
        { key: 'UNARCHIVE', icon: <InboxOutlined />, label: '恢复到课程主页' },
        ...(course.role === 'CREATOR' && course.status === 'ARCHIVED'
          ? [{ key: 'RESTORE_FOR_ALL', icon: <InboxOutlined />, label: '恢复全部成员课程' }]
          : []),
        { type: 'divider' as const },
        course.role === 'CREATOR'
          ? { key: 'DELETE', danger: true, icon: <DeleteOutlined />, label: '移入回收站' }
          : { key: 'LEAVE', danger: true, icon: <DeleteOutlined />, label: '退出课程' },
      ]
    : [
        { key: 'ARCHIVE', icon: <InboxOutlined />, label: '仅归档自己' },
        ...(course.role === 'CREATOR'
          ? [
              { key: 'ARCHIVE_FOR_ALL', icon: <InboxOutlined />, label: '归档全部成员课程' },
              { type: 'divider' as const },
              { key: 'DELETE', danger: true, icon: <DeleteOutlined />, label: '移入回收站' },
            ]
          : [
              { type: 'divider' as const },
              { key: 'LEAVE', danger: true, icon: <DeleteOutlined />, label: '退出课程' },
            ]),
      ]

  return (
    <div ref={setNodeRef} style={style}>
      <Card
        className="course-card"
        cover={
          <div
            className="course-cover"
            style={course.coverUrl ? { backgroundImage: `url(${course.coverUrl})` } : undefined}
          >
            {!course.coverUrl && <span>{course.name.slice(0, 1)}</span>}
            <Button
              className="drag-handle"
              type="text"
              icon={<HolderOutlined />}
              aria-label="拖动排序"
              {...attributes}
              {...listeners}
            />
            <Dropdown
              trigger={['click']}
              menu={{ items, onClick: ({ key }) => onAction(course, key) }}
            >
              <Button className="course-menu" type="text" icon={<MoreOutlined />} />
            </Dropdown>
          </div>
        }
      >
        <Space direction="vertical" size={10} className="course-card-body">
          <div>
            <Typography.Title level={4} ellipsis={{ rows: 1 }} className="course-title">
              {course.name}
            </Typography.Title>
            <Typography.Text type="secondary">课程号：{course.courseCode}</Typography.Text>
          </div>
          <div className="course-meta">
            <Space size={6}>
              <TeamOutlined />
              <span>{course.memberCount} 人</span>
            </Space>
            <Space size={6}>
              <Tag color={course.role === 'STUDENT' ? 'green' : 'blue'}>
                {roleLabel[course.role]}
              </Tag>
              {course.status === 'ARCHIVED' && <Tag>全局归档</Tag>}
            </Space>
          </div>
          <Button type="link" className="course-open" onClick={() => onOpen(course.id)}>
            进入课程
          </Button>
        </Space>
      </Card>
    </div>
  )
}
