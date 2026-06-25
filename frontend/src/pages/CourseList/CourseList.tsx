import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Row, Col, Card, Button, Modal, Form, Input, Switch,
  Typography, Empty, message, Space, Tag, Spin,
} from 'antd';
import {
  PlusOutlined, UsergroupAddOutlined, BookOutlined,
  TeamOutlined, CrownOutlined,
} from '@ant-design/icons';
import { courseApi, type Course } from '../../api';

const { Title, Text } = Typography;

export default function CourseList() {
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [showArchived, setShowArchived] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [joinOpen, setJoinOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [createForm] = Form.useForm();
  const [joinForm] = Form.useForm();
  const navigate = useNavigate();

  const fetchCourses = async () => {
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
  };

  useEffect(() => {
    fetchCourses();
  }, [showArchived]);

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
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            创建课程
          </Button>
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
        <Row gutter={[16, 16]}>
          {courses.map((course) => (
            <Col key={course.id} xs={24} sm={12} md={8} lg={6}>
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
          ))}
        </Row>
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
