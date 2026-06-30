import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Tabs, Card, Button, Table, Tag, Space, Typography,
  Modal, Form, Input, InputNumber, DatePicker, Switch,
  message, Spin, Empty, Popconfirm, List, Tooltip, Descriptions,
} from 'antd';
import {
  PlusOutlined, SendOutlined, EditOutlined, CloseCircleOutlined,
  BellOutlined, TeamOutlined, FileTextOutlined, ArrowLeftOutlined,
  CrownOutlined, FolderOutlined, CommentOutlined, RobotOutlined, SearchOutlined,
} from '@ant-design/icons';
import MaterialsTab from './MaterialsTab';
import TopicsTab from './TopicsTab';
import dayjs from 'dayjs';
import { courseApi, assignmentsApi, submissionsApi, type Course } from '../../api';
import { useAuth } from '../../contexts/AuthContext';

const { Title, Text, Paragraph } = Typography;

interface AssignmentItem {
  id: number;
  courseId: number;
  title: string;
  content?: string;
  status: string;
  deadline?: string;
  maxScore: number;
  allowResubmit: boolean;
  createTime: string;
}

interface MemberItem {
  id: number;
  userId: number;
  username: string;
  realName?: string;
  avatarUrl?: string;
  role: string;
  joinedAt: string;
}

export default function CourseDetail() {
  const { courseId } = useParams<{ courseId: string }>();
  const numCourseId = Number(courseId);
  const { user } = useAuth();
  const navigate = useNavigate();

  const [course, setCourse] = useState<Course | null>(null);
  const [assignments, setAssignments] = useState<AssignmentItem[]>([]);
  const [members, setMembers] = useState<MemberItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('assignments');

  // Create assignment modal
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  // Edit assignment modal
  const [editOpen, setEditOpen] = useState(false);
  const [editForm] = Form.useForm();
  const [editingId, setEditingId] = useState<number | null>(null);

  const isTeacher = course?.role === 'CREATOR' || course?.role === 'TEACHER';

  const disabledDate = (current: dayjs.Dayjs | null) => {
    if (!current) {
      return false;
    }
    return current.isBefore(dayjs().startOf('day'));
  };

  const fetchCourse = async () => {
    try {
      const data = await courseApi.getCourseDetail(numCourseId) as unknown as Course;
      setCourse(data);
    } catch {
      message.error('获取课程信息失败');
    }
  };

  const fetchAssignments = async () => {
    try {
      const data = await assignmentsApi.getCourseAssignments(numCourseId) as unknown as AssignmentItem[];
      setAssignments(data || []);
    } catch {
      message.error('获取作业列表失败');
    }
  };

  const fetchMembers = async () => {
    try {
      const data = await courseApi.getCourseMembers(numCourseId, { size: 50 }) as unknown as { content: MemberItem[] };
      setMembers(data?.content || []);
    } catch {
      message.error('获取成员列表失败');
    }
  };

  useEffect(() => {
    setLoading(true);
    Promise.all([fetchCourse(), fetchAssignments(), fetchMembers()]).finally(() => setLoading(false));
  }, [courseId]);

  // ====== 作业操作 ======
  const handleCreate = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      await assignmentsApi.createAssignment({
        courseId: numCourseId,
        title: values.title as string,
        content: values.content as string | undefined,
        deadline: values.deadline
          ? dayjs(values.deadline as string).format('YYYY-MM-DD HH:mm:ss')
          : undefined,
        maxScore: values.maxScore as number | undefined,
        allowResubmit: values.allowResubmit as boolean | undefined,
      });
      message.success('作业创建成功');
      setCreateOpen(false);
      createForm.resetFields();
      fetchAssignments();
    } catch {
      message.error('创建作业失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handlePublish = async (assignmentId: number) => {
    try {
      await assignmentsApi.updateAssignmentStatus(assignmentId, { status: 'PUBLISHED' });
      message.success('作业已发布');
      fetchAssignments();
    } catch {
      message.error('发布失败');
    }
  };

  const handleClose = async (assignmentId: number) => {
    try {
      await assignmentsApi.updateAssignmentStatus(assignmentId, { status: 'CLOSED' });
      message.success('作业已关闭');
      fetchAssignments();
    } catch {
      message.error('关闭失败');
    }
  };

  const handleUrge = async (assignmentId: number) => {
    try {
      const result: any = await assignmentsApi.urgeAssignment(assignmentId);
      message.success(`已催交 ${result?.urgedCount || 0} 名学生`);
    } catch {
      message.error('催交失败');
    }
  };

  const handleEditOpen = (a: AssignmentItem) => {
    setEditingId(a.id);
    editForm.setFieldsValue({
      title: a.title,
      content: a.content,
      deadline: a.deadline ? dayjs(a.deadline) : null,
      maxScore: a.maxScore,
      allowResubmit: a.allowResubmit,
    });
    setEditOpen(true);
  };

  const handleEdit = async (values: Record<string, unknown>) => {
    if (!editingId) return;
    setSubmitting(true);
    try {
      await assignmentsApi.updateAssignment(editingId, {
        title: values.title as string | undefined,
        content: values.content as string | undefined,
        deadline: values.deadline
          ? dayjs(values.deadline as string).format('YYYY-MM-DD HH:mm:ss')
          : undefined,
        maxScore: values.maxScore as number | undefined,
        allowResubmit: values.allowResubmit as boolean | undefined,
      });
      message.success('作业已更新');
      setEditOpen(false);
      fetchAssignments();
    } catch {
      message.error('更新失败');
    } finally {
      setSubmitting(false);
    }
  };

  // ====== 成员操作 ======
  const handleRoleChange = async (memberUserId: number, newRole: string) => {
    try {
      await courseApi.updateMemberRole(numCourseId, memberUserId, {
        role: newRole as 'TEACHER' | 'STUDENT',
      });
      message.success('角色已更新');
      fetchMembers();
    } catch {
      message.error('更新角色失败');
    }
  };

  // ====== 成员表格列 ======
  const memberColumns = [
    { title: '用户名', dataIndex: 'username', key: 'username' },
    { title: '姓名', dataIndex: 'realName', key: 'realName', render: (v?: string) => v || '-' },
    {
      title: '角色', dataIndex: 'role', key: 'role',
      render: (role: string) => {
        const colors: Record<string, string> = { CREATOR: 'gold', TEACHER: 'blue', STUDENT: 'green' };
        const labels: Record<string, string> = { CREATOR: '创建者', TEACHER: '教师', STUDENT: '学生' };
        return <Tag color={colors[role] || 'default'}>{labels[role] || role}</Tag>;
      },
    },
    ...(isTeacher ? [{
      title: '操作', key: 'action',
      render: (_: unknown, record: MemberItem) => {
        if (record.role === 'CREATOR') return null;
        return (
          <Space>
            {record.role === 'STUDENT' ? (
              <Button size="small" onClick={() => handleRoleChange(record.userId, 'TEACHER')}>设为教师</Button>
            ) : (
              <Button size="small" onClick={() => handleRoleChange(record.userId, 'STUDENT')}>降为学生</Button>
            )}
          </Space>
        );
      },
    }] : []),
  ];

  const statusTag = (status: string) => {
    const config: Record<string, { color: string; text: string }> = {
      DRAFT: { color: 'default', text: '草稿' },
      PUBLISHED: { color: 'blue', text: '已发布' },
      CLOSED: { color: 'red', text: '已关闭' },
    };
    const c = config[status] || { color: 'default', text: status };
    return <Tag color={c.color}>{c.text}</Tag>;
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>;
  }

  if (!course) {
    return <Empty description="课程不存在" />;
  }

  return (
    <div>
      {/* Course Header */}
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/courses')}
        style={{ marginBottom: 12 }}
      >
        返回课程列表
      </Button>
      <Card style={{ marginBottom: 16 }}>
        <Descriptions title={<Title level={4}>{course.name}</Title>} column={2} size="small">
          <Descriptions.Item label="课程号">
            <Text copyable>{course.courseCode}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="成员数">
            <TeamOutlined /> {course.memberCount} 人
          </Descriptions.Item>
          <Descriptions.Item label="我的角色">
            <Tag color={isTeacher ? 'blue' : 'green'}>
              {course.role === 'CREATOR' ? '创建者（教师）' : course.role === 'TEACHER' ? '教师' : '学生'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            {course.status === 'ARCHIVED' ? <Tag color="orange">已归档</Tag> : <Tag color="green">活跃</Tag>}
          </Descriptions.Item>
        </Descriptions>
        <div style={{ marginTop: 12 }}>
          <Button
            type="default"
            icon={<RobotOutlined />}
            onClick={() => navigate(`/courses/${courseId}/ai-chat`)}
          >
            AI 答疑
          </Button>
        </div>
      </Card>

      {/* Tabs */}
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'assignments',
          label: <span><FileTextOutlined /> 作业列表</span>,
          children: (
            <div>
              {isTeacher && (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => { createForm.resetFields(); setCreateOpen(true); }}
                  style={{ marginBottom: 16 }}
                >
                  创建作业
                </Button>
              )}
              {assignments.length === 0 ? (
                <Empty description="暂无作业" />
              ) : (
                <List
                  dataSource={assignments}
                  renderItem={(a: AssignmentItem) => (
                    <Card
                      size="small"
                      style={{ marginBottom: 12 }}
                      title={
                        <Space>
                          {statusTag(a.status)}
                          <Text strong>{a.title}</Text>
                        </Space>
                      }
                      extra={
                        <Space>
                          {/* Student: view/submit */}
                          {!isTeacher && a.status === 'PUBLISHED' && (
                            <Button
                              type="primary"
                              size="small"
                              onClick={() => navigate(`/courses/${courseId}/assignments/${a.id}`)}
                            >
                              查看/提交
                            </Button>
                          )}
                          {/* Teacher actions */}
                          {isTeacher && (
                            <>
                              {a.status === 'DRAFT' && (
                                <>
                                  <Button size="small" icon={<EditOutlined />} onClick={() => handleEditOpen(a)}>
                                    编辑
                                  </Button>
                                  <Popconfirm title="确认发布？发布后学生可见" onConfirm={() => handlePublish(a.id)}>
                                    <Button size="small" type="primary" icon={<SendOutlined />}>发布</Button>
                                  </Popconfirm>
                                </>
                              )}
                              {a.status === 'PUBLISHED' && (
                                <>
                                  <Button size="small" icon={<EditOutlined />} onClick={() => handleEditOpen(a)}>
                                    修正
                                  </Button>
                                  <Button
                                    size="small"
                                    onClick={() => navigate(`/courses/${courseId}/assignments/${a.id}`)}
                                  >
                                    查看提交
                                  </Button>
                                  <Button
                                    size="small"
                                    icon={<SearchOutlined />}
                                    onClick={() => navigate(`/courses/${courseId}/assignments/${a.id}/similarity`)}
                                  >
                                    相似度
                                  </Button>
                                  <Popconfirm title="确认关闭？" onConfirm={() => handleClose(a.id)}>
                                    <Button size="small" icon={<CloseCircleOutlined />}>关闭</Button>
                                  </Popconfirm>
                                  <Tooltip title="催交未提交学生">
                                    <Button size="small" icon={<BellOutlined />} onClick={() => handleUrge(a.id)}>
                                      催交
                                    </Button>
                                  </Tooltip>
                                </>
                              )}
                              {a.status === 'CLOSED' && (
                                <>
                                  <Button
                                    size="small"
                                    onClick={() => navigate(`/courses/${courseId}/assignments/${a.id}`)}
                                  >
                                    查看提交
                                  </Button>
                                  <Button
                                    size="small"
                                    icon={<SearchOutlined />}
                                    onClick={() => navigate(`/courses/${courseId}/assignments/${a.id}/similarity`)}
                                  >
                                    相似度
                                  </Button>
                                </>
                              )}
                            </>
                          )}
                        </Space>
                      }
                    >
                      {a.content && <Paragraph ellipsis={{ rows: 2 }}>{a.content}</Paragraph>}
                      <Space size="large">
                        <Text type="secondary">满分：{a.maxScore} 分</Text>
                        {a.deadline && <Text type={dayjs(a.deadline).isBefore(dayjs()) ? 'danger' : 'secondary'}>
                          截止：{dayjs(a.deadline).format('YYYY-MM-DD HH:mm')}
                        </Text>}
                        <Text type="secondary">
                          {a.allowResubmit ? '允许重复提交' : '不允许重复提交'}
                        </Text>
                      </Space>
                    </Card>
                  )}
                />
              )}
            </div>
          ),
        },
        {
          key: 'members',
          label: <span><TeamOutlined /> 成员列表 ({members.length})</span>,
          children: (
            <Table
              dataSource={members}
              columns={memberColumns}
              rowKey="userId"
              pagination={false}
              size="middle"
            />
          ),
        },
        {
          key: 'materials',
          label: <span><FolderOutlined /> 课程资料</span>,
          children: <MaterialsTab courseId={numCourseId} isTeacher={!!isTeacher} />,
        },
        {
          key: 'topics',
          label: <span><CommentOutlined /> 话题讨论</span>,
          children: <TopicsTab courseId={numCourseId} isTeacher={!!isTeacher} />,
        },
      ]} />

      {/* Create Assignment Modal */}
      <Modal
        title="创建作业"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        footer={null}
        destroyOnClose
        width={600}
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate}
          initialValues={{ maxScore: 100, allowResubmit: true }}>
          <Form.Item name="title" label="作业标题" rules={[{ required: true, message: '请输入作业标题' }]}>
            <Input placeholder="例如：第一次作业" />
          </Form.Item>
          <Form.Item name="content" label="作业要求">
            <Input.TextArea rows={4} placeholder="请描述作业要求..." />
          </Form.Item>
          <Form.Item name="maxScore" label="满分">
            <InputNumber min={1} max={999} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="deadline" label="截止时间">
            <DatePicker showTime format="YYYY-MM-DD HH:mm:ss" style={{ width: '100%' }} disabledDate={disabledDate} />
          </Form.Item>
          <Form.Item name="allowResubmit" label="允许重复提交" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>
              创建作业
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Assignment Modal */}
      <Modal
        title="编辑作业"
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        footer={null}
        destroyOnClose
        width={600}
      >
        <Form form={editForm} layout="vertical" onFinish={handleEdit}>
          <Form.Item name="title" label="作业标题">
            <Input />
          </Form.Item>
          <Form.Item name="content" label="作业要求">
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="maxScore" label="满分">
            <InputNumber min={1} max={999} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="deadline" label="截止时间">
            <DatePicker showTime format="YYYY-MM-DD HH:mm:ss" style={{ width: '100%' }} disabledDate={disabledDate} />
          </Form.Item>
          <Form.Item name="allowResubmit" label="允许重复提交" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>
              保存修改
            </Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
