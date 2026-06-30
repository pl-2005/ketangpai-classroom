import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Row, Button, Modal, Form, Input, Switch,
  Typography, Empty, message, Space, Spin, Card,
} from 'antd';
import {
  PlusOutlined, UsergroupAddOutlined, BookOutlined,
} from '@ant-design/icons';
import {
  DndContext, DragOverlay, closestCenter,
  PointerSensor, KeyboardSensor, useSensor, useSensors,
  type DragStartEvent, type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext, rectSortingStrategy,
  sortableKeyboardCoordinates, arrayMove,
} from '@dnd-kit/sortable';
import { courseApi, type Course } from '../../api';
import { useAuth } from '../../contexts/AuthContext';
import SortableCourseCard from './SortableCourseCard';
import './CourseList.css';

const { Title } = Typography;

export default function CourseList() {
  const { user } = useAuth();
  const isTeacher = user?.role === 'TEACHER';
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [showArchived, setShowArchived] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [joinOpen, setJoinOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [createForm] = Form.useForm();
  const [joinForm] = Form.useForm();
  const navigate = useNavigate();

  // 拖拽传感器：需要移动 5px 才激活拖拽，小于 5px 视为点击
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 5,
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  const fetchCourses = useCallback(async () => {
    setLoading(true);
    try {
      const result = await courseApi.getMyCourses({
        archived: showArchived,
        size: 50,
      }) as unknown as { content: Course[] };
      setCourses(result?.content || []);
    } catch {
      message.error('获取课程列表失败');
    } finally {
      setLoading(false);
    }
  }, [showArchived]);

  useEffect(() => {
    fetchCourses();
  }, [fetchCourses]);

  // ---- 拖拽事件处理 ----

  const handleDragStart = useCallback((event: DragStartEvent) => {
    setActiveId(Number(event.active.id));
  }, []);

  const handleDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event;
    setActiveId(null);

    if (!over || active.id === over.id) {
      return; // 未移动，无需处理
    }

    const oldIndex = courses.findIndex((c) => c.id === Number(active.id));
    const newIndex = courses.findIndex((c) => c.id === Number(over.id));

    if (oldIndex === -1 || newIndex === -1) {
      return;
    }

    // 乐观更新：立即更新本地状态
    const reordered = arrayMove(courses, oldIndex, newIndex);
    setCourses(reordered);

    // 持久化到后端，失败则回滚
    const courseIds = reordered.map((c) => c.id);
    courseApi.updateSortOrder({ courseIds }).catch(() => {
      message.error('排序更新失败');
      fetchCourses();
    });
  }, [courses, fetchCourses]);

  const handleDragCancel = useCallback(() => {
    setActiveId(null);
  }, []);

  // ---- 课程操作 ----

  const handleCreate = async (values: { name: string; description?: string }) => {
    setSubmitting(true);
    try {
      await courseApi.createCourse(values);
      message.success('课程创建成功');
      setCreateOpen(false);
      createForm.resetFields();
      fetchCourses();
    } catch {
      message.error('创建课程失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleJoin = async (values: { courseCode: string }) => {
    setSubmitting(true);
    try {
      await courseApi.joinCourse(values);
      message.success('加入课程成功');
      setJoinOpen(false);
      joinForm.resetFields();
      fetchCourses();
    } catch {
      message.error('加入课程失败，请检查课程号');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>
          <BookOutlined style={{ marginRight: 8 }} />
          我的课程
        </Title>
        <Space>
          <span>显示归档</span>
          <Switch checked={showArchived} onChange={setShowArchived} />
          <Button icon={<UsergroupAddOutlined />} onClick={() => setJoinOpen(true)}>
            加入课程
          </Button>
          {isTeacher && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              创建课程
            </Button>
          )}
        </Space>
      </div>

      {/* Course Grid */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>
      ) : courses.length === 0 ? (
        <Empty
          description={showArchived ? '暂无归档课程' : '暂无课程，快去创建或加入一个吧！'}
          style={{ padding: 60 }}
        />
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragStart={handleDragStart}
          onDragEnd={handleDragEnd}
          onDragCancel={handleDragCancel}
        >
          <SortableContext
            items={courses.map((c) => c.id)}
            strategy={rectSortingStrategy}
          >
            <Row gutter={[16, 16]}>
              {courses.map((course) => (
                <SortableCourseCard key={course.id} course={course} onRefresh={fetchCourses} />
              ))}
            </Row>
          </SortableContext>

          <DragOverlay>
            {activeId != null ? (
              <div className="course-card-drag-overlay">
                <Card
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
                >
                  <Card.Meta
                    title={courses.find((c) => c.id === activeId)?.name ?? ''}
                    description="拖拽到目标位置"
                  />
                </Card>
              </div>
            ) : null}
          </DragOverlay>
        </DndContext>
      )}

      {/* Create Course Modal */}
      <Modal
        title="创建课程"
        open={createOpen}
        onCancel={() => { setCreateOpen(false); createForm.resetFields(); }}
        footer={null}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="name"
            label="课程名称"
            rules={[{ required: true, message: '请输入课程名称' }]}
          >
            <Input placeholder="例如：软件工程与计算 II" />
          </Form.Item>
          <Form.Item name="description" label="课程描述">
            <Input.TextArea rows={3} placeholder="可选：简要描述课程内容" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>
              创建课程
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* Join Course Modal */}
      <Modal
        title="加入课程"
        open={joinOpen}
        onCancel={() => { setJoinOpen(false); joinForm.resetFields(); }}
        footer={null}
        destroyOnClose
      >
        <Form form={joinForm} layout="vertical" onFinish={handleJoin}>
          <Form.Item
            name="courseCode"
            label="课程号"
            rules={[{ required: true, message: '请输入课程号' }]}
            extra="请向教师获取课程号"
          >
            <Input placeholder="请输入 6 位课程号" maxLength={6} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>
              加入课程
            </Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
