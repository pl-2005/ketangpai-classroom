import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { List, Card, Button, Tag, Space, Typography, Modal, Form, Input, Switch, message, Spin, Empty } from 'antd';
import { PlusOutlined, PushpinOutlined, LockOutlined, CommentOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { topicsApi, type Topic } from '../../api';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

const { Text, Paragraph } = Typography;

interface Props {
  courseId: number;
  isTeacher: boolean;
}

export default function TopicsTab({ courseId }: Props) {
  const navigate = useNavigate();
  const [topics, setTopics] = useState<Topic[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const fetchTopics = useCallback(async () => {
    setLoading(true);
    try {
      const data = await topicsApi.getCourseTopics(courseId) as unknown as { content?: Topic[] };
      const list = Array.isArray(data) ? data : (data?.content || []);
      setTopics(list);
    } catch {
      message.error('获取话题列表失败');
    } finally {
      setLoading(false);
    }
  }, [courseId]);

  useEffect(() => {
    fetchTopics();
  }, [fetchTopics]);

  const handleCreate = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      await topicsApi.createTopic({
        courseId,
        title: values.title as string,
        content: values.content as string,
        isAnonymous: values.isAnonymous as boolean | undefined,
      });
      message.success('话题创建成功');
      setCreateOpen(false);
      createForm.resetFields();
      fetchTopics();
    } catch {
      message.error('创建话题失败');
    } finally {
      setSubmitting(false);
    }
  };

  const statusTag = (status: string) => {
    const config: Record<string, { color: string; text: string; icon: React.ReactNode }> = {
      PINNED: { color: 'gold', text: '置顶', icon: <PushpinOutlined /> },
      LOCKED: { color: 'red', text: '已锁定', icon: <LockOutlined /> },
      NORMAL: { color: 'default', text: '正常', icon: null },
    };
    const c = config[status] || config.NORMAL;
    return <Tag color={c.color} icon={c.icon}>{c.text}</Tag>;
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>;
  }

  return (
    <div>
      <Button
        type="primary"
        icon={<PlusOutlined />}
        onClick={() => { createForm.resetFields(); setCreateOpen(true); }}
        style={{ marginBottom: 16 }}
      >
        发布话题
      </Button>

      {topics.length === 0 ? (
        <Empty description="暂无话题讨论" />
      ) : (
        <List
          dataSource={topics}
          renderItem={(topic: Topic) => (
            <Card
              size="small"
              style={{ marginBottom: 12, cursor: 'pointer' }}
              onClick={() => navigate(`/courses/${courseId}/topics/${topic.id}`)}
              hoverable
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <Space direction="vertical" size="small" style={{ flex: 1 }}>
                  <Space>
                    {statusTag(topic.status)}
                    <Text strong style={{ fontSize: 16 }}>{topic.title}</Text>
                  </Space>
                  <Paragraph ellipsis={{ rows: 2 }} type="secondary" style={{ marginBottom: 0 }}>
                    {topic.content}
                  </Paragraph>
                </Space>
                <Space direction="vertical" size="small" style={{ textAlign: 'right', marginLeft: 16, minWidth: 100 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {topic.isAnonymous ? '匿名用户' : (topic.authorName || `用户${topic.authorId}`)}
                  </Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {topic.createTime ? dayjs(topic.createTime).fromNow() : ''}
                  </Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    <CommentOutlined /> {topic.replyCount || 0} 回复
                  </Text>
                </Space>
              </div>
            </Card>
          )}
        />
      )}

      {/* Create Topic Modal */}
      <Modal
        title="发布话题"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        footer={null}
        destroyOnClose
        width={560}
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate}
          initialValues={{ isAnonymous: false }}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入话题标题' }]}>
            <Input placeholder="话题标题" maxLength={200} />
          </Form.Item>
          <Form.Item name="content" label="内容" rules={[{ required: true, message: '请输入话题内容' }]}>
            <Input.TextArea rows={5} placeholder="请输入内容..." />
          </Form.Item>
          <Form.Item name="isAnonymous" label="匿名发布" valuePropName="checked">
            <Switch checkedChildren="匿名" unCheckedChildren="实名" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>发布</Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
