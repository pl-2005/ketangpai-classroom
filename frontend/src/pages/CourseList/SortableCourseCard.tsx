import { useState } from 'react';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Col, Card, Tag, Space, Typography, Button, message, Tooltip } from 'antd';
import {
  BookOutlined, TeamOutlined, CrownOutlined, InboxOutlined, UndoOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { courseApi, type Course } from '../../api';

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

  const handleArchive = async (e: React.MouseEvent) => {
    e.stopPropagation(); // 阻止触发卡片点击导航
    setArchiving(true);
    try {
      if (course.isArchived) {
        await courseApi.courseAction(course.id, { action: 'UNARCHIVE' });
        message.success('已取消归档');
      } else {
        await courseApi.courseAction(course.id, { action: 'ARCHIVE' });
        message.success('已归档');
      }
      onRefresh();
    } catch {
      message.error('操作失败');
    } finally {
      setArchiving(false);
    }
  };

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
        actions={[
          <Tooltip title={course.isArchived ? '取消归档' : '归档'} key="archive">
            <Button
              type="text"
              size="small"
              icon={course.isArchived ? <UndoOutlined /> : <InboxOutlined />}
              loading={archiving}
              onClick={handleArchive}
              style={{ fontSize: 14 }}
            />
          </Tooltip>,
        ]}
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
                {course.status === 'ARCHIVED' && <Tag color="orange">已归档</Tag>}
              </Space>
            </div>
          }
        />
      </Card>
    </Col>
  );
}
