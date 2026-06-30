import { useState } from 'react';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Col, Card, Tag, Space, Typography, Button, message, Tooltip, Popconfirm } from 'antd';
import {
  BookOutlined, TeamOutlined, CrownOutlined, InboxOutlined, UndoOutlined, DeleteOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { courseApi, type Course, type CourseAction } from '../../api';

const { Text } = Typography;

interface SortableCourseCardProps {
  course: Course;
  onRefresh: () => void;
}

/**
 * 可拖拽排序的课程卡片。
 * 使用 @dnd-kit/sortable 的 useSortable hook，
 * 拖拽激活阈值 5px 由父组件 DndContext 的 PointerSensor 控制，
 * 区分点击导航和拖拽排序。
 */
export default function SortableCourseCard({ course, onRefresh }: SortableCourseCardProps) {
  const navigate = useNavigate();
  const [archiving, setArchiving] = useState(false);
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: course.id });

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.3 : 1,
    cursor: isDragging ? 'grabbing' : 'grab',
  };

  const roleTag = (role: string) => {
    switch (role) {
      case 'CREATOR':
        return <Tag color="gold" icon={<CrownOutlined />}>创建者</Tag>;
      case 'TEACHER':
        return <Tag color="blue">教师</Tag>;
      default:
        return <Tag color="green">学生</Tag>;
    }
  };

  const doAction = async (action: CourseAction, label: string) => {
    setArchiving(true);
    try {
      await courseApi.courseAction(course.id, { action });
      message.success(`${label}成功`);
      onRefresh();
    } catch {
      message.error(`${label}失败`);
    } finally {
      setArchiving(false);
    }
  };

  const handlePersonalArchive = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (course.isArchived) {
      doAction('UNARCHIVE', '取消归档');
    } else {
      doAction('ARCHIVE', '个人归档');
    }
  };

  const isCreator = course.role === 'CREATOR';
  const isCourseArchived = course.status === 'ARCHIVED';

  // 底部操作按钮
  const cardActions: React.ReactNode[] = [];

  if (isCreator && isCourseArchived) {
    // 创建者查看已归档课程：恢复 + 永久删除
    cardActions.push(
      <Popconfirm
        key="restore"
        title="确认恢复课程为活跃状态？"
        onConfirm={() => doAction('RESTORE_FOR_ALL', '恢复课程')}
        okText="确认恢复"
        cancelText="取消"
      >
        <Tooltip title="恢复课程">
          <Button
            type="text"
            size="small"
            icon={<UndoOutlined />}
            loading={archiving}
            onClick={(e) => e.stopPropagation()}
            style={{ fontSize: 14, color: '#1677ff' }}
          />
        </Tooltip>
      </Popconfirm>,
    );
    cardActions.push(
      <Popconfirm
        key="delete"
        title="永久删除后无法恢复，确认删除？"
        description="课程及所有作业、资料将被永久删除"
        onConfirm={() => doAction('DELETE', '删除课程')}
        okText="确认删除"
        okButtonProps={{ danger: true }}
        cancelText="取消"
      >
        <Tooltip title="永久删除">
          <Button
            type="text"
            size="small"
            danger
            icon={<DeleteOutlined />}
            loading={archiving}
            onClick={(e) => e.stopPropagation()}
            style={{ fontSize: 14 }}
          />
        </Tooltip>
      </Popconfirm>,
    );
  } else {
    // 普通成员或活跃课程：个人归档/取消归档
    cardActions.push(
      <Tooltip title={course.isArchived ? '取消归档' : '归档'} key="archive">
        <Button
          type="text"
          size="small"
          icon={course.isArchived ? <UndoOutlined /> : <InboxOutlined />}
          loading={archiving}
          onClick={handlePersonalArchive}
          style={{ fontSize: 14 }}
        />
      </Tooltip>,
    );
  }

  return (
    <Col
      xs={24} sm={12} md={8} lg={6}
      ref={setNodeRef}
      style={style}
      {...attributes}
      {...listeners}
    >
      <Card
        hoverable
        onClick={() => navigate(`/courses/${course.id}`)}
        cover={
          <div
            style={{
              height: 120,
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <BookOutlined style={{ fontSize: 40, color: '#fff', opacity: 0.8 }} />
          </div>
        }
        actions={cardActions}
      >
        <Card.Meta
          title={
            <Space>
              <Text strong ellipsis style={{ maxWidth: 140 }}>{course.name}</Text>
              {roleTag(course.role)}
            </Space>
          }
          description={
            <div>
              <div style={{ marginBottom: 4 }}>
                <Text type="secondary" copyable={{ text: course.courseCode }}>
                  课程号：{course.courseCode}
                </Text>
              </div>
              <Space>
                <span><TeamOutlined /> {course.memberCount} 人</span>
                {isCourseArchived && <Tag color="orange">已归档</Tag>}
              </Space>
            </div>
          }
        />
      </Card>
    </Col>
  );
}
