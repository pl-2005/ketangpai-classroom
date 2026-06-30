import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Button, Tag, Space, Typography, Input, Switch, Modal, Form,
  message, Spin, Empty, Popconfirm, List, Divider,
} from 'antd';
import {
  ArrowLeftOutlined, PushpinOutlined, LockOutlined, UnlockOutlined,
  DeleteOutlined, EditOutlined, CommentOutlined, StopOutlined, CheckCircleOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { topicsApi, type Topic, type TopicReply, type TopicStatus } from '../../api';
import { useAuth } from '../../contexts/AuthContext';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

const { Text, Paragraph, Title } = Typography;
const { TextArea } = Input;

export default function TopicDetail() {
  const { courseId, topicId } = useParams<{ courseId: string; topicId: string }>();
  const numCourseId = Number(courseId);
  const numTopicId = Number(topicId);
  const navigate = useNavigate();
  const { user } = useAuth();

  const [topic, setTopic] = useState<Topic | null>(null);
  const [replies, setReplies] = useState<TopicReply[]>([]);
  const [loading, setLoading] = useState(true);

  // Reply form
  const [replyContent, setReplyContent] = useState('');
  const [replyAnonymous, setReplyAnonymous] = useState(false);
  const [replyParentId, setReplyParentId] = useState<number | null>(null);
  const [replyParentAuthor, setReplyParentAuthor] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Edit topic
  const [editOpen, setEditOpen] = useState(false);
  const [editForm] = Form.useForm();

  // Edit reply
  const [editReplyId, setEditReplyId] = useState<number | null>(null);
  const [editReplyContent, setEditReplyContent] = useState('');

  const fetchTopic = useCallback(async () => {
    setLoading(true);
    try {
      const data = await topicsApi.getTopicDetail(numTopicId) as unknown as {
        topic: Topic;
        replies: TopicReply[] | { content: TopicReply[] };
      };
      setTopic(data.topic);
      const replyList = Array.isArray(data.replies) ? data.replies : (data.replies?.content || []);
      setReplies(replyList);
    } catch {
      message.error('获取话题详情失败');
    } finally {
      setLoading(false);
    }
  }, [numTopicId]);

  useEffect(() => {
    fetchTopic();
  }, [fetchTopic]);

  const isAuthor = topic?.authorId === user?.id || (topic?.authorId === null && topic?.isAnonymous && false);
  const isTeacherRole = true; // Will check via topic course membership
  const canModerate = isAuthor || isTeacherRole;

  const handleReply = async () => {
    if (!replyContent.trim()) {
      message.warning('请输入回复内容');
      return;
    }
    setSubmitting(true);
    try {
      await topicsApi.replyTopic(numTopicId, {
        content: replyContent,
        isAnonymous: replyAnonymous,
        parentId: replyParentId || undefined,
      });
      message.success('回复成功');
      setReplyContent('');
      setReplyAnonymous(false);
      setReplyParentId(null);
      setReplyParentAuthor('');
      fetchTopic();
    } catch {
      message.error('回复失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleReplyTo = (reply: TopicReply) => {
    setReplyParentId(reply.id);
    setReplyParentAuthor(reply.isAnonymous ? '匿名用户' : (reply.authorName || '用户'));
    // Focus textarea
    document.getElementById('reply-textarea')?.focus();
  };

  const handleStatusChange = async (status: TopicStatus) => {
    try {
      await topicsApi.updateTopicStatus(numTopicId, { status });
      message.success('状态已更新');
      fetchTopic();
    } catch { message.error('状态更新失败'); }
  };

  const handleToggleDiscussion = async () => {
    try {
      const res = await topicsApi.toggleDiscussion(numTopicId) as unknown as Topic;
      message.success(res.discussionEnabled ? '已开启讨论' : '已关闭讨论');
      fetchTopic();
    } catch { message.error('操作失败'); }
  };

  const handleDeleteTopic = async () => {
    try {
      await topicsApi.deleteTopic(numTopicId);
      message.success('话题已删除');
      navigate(`/courses/${courseId}`);
    } catch { message.error('删除话题失败'); }
  };

  const handleEditTopic = async (values: Record<string, unknown>) => {
    try {
      await topicsApi.updateTopic(numTopicId, {
        title: values.title as string,
        content: values.content as string,
      });
      message.success('话题已更新');
      setEditOpen(false);
      fetchTopic();
    } catch { message.error('编辑话题失败'); }
  };

  const handleDeleteReply = async (replyId: number) => {
    try {
      await topicsApi.deleteReply(replyId);
      message.success('回复已删除');
      fetchTopic();
    } catch { message.error('删除回复失败'); }
  };

  const handleEditReply = async () => {
    if (!editReplyId) return;
    try {
      // We don't have updateReply API, use replyTopic as workaround...
      // Actually let's just skip edit reply for now - API doesn't support it directly
      message.info('编辑回复功能暂不支持');
      setEditReplyId(null);
    } catch { /* handled */ }
  };

  const getReplyDepth = (path: string) => {
    if (!path) return 0;
    return path.split('/').filter(Boolean).length - 1;
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

  if (!topic) {
    return <Empty description="话题不存在" />;
  }

  return (
    <div>
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate(`/courses/${courseId}`)}
        style={{ marginBottom: 12 }}
      >
        返回课程
      </Button>

      {/* Topic Header */}
      <Card style={{ marginBottom: 16 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Space>
            {statusTag(topic.status)}
            <Title level={4} style={{ margin: 0 }}>{topic.title}</Title>
          </Space>

          <Space>
            <Text type="secondary">
              {topic.isAnonymous ? '匿名用户' : (topic.authorName || `用户${topic.authorId}`)}
            </Text>
            <Text type="secondary">·</Text>
            <Text type="secondary">{dayjs(topic.createTime).fromNow()}</Text>
            {!topic.discussionEnabled && <Tag color="warning">讨论已关闭</Tag>}
          </Space>

          <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
            {topic.content}
          </Paragraph>

          {/* Teacher actions */}
          <Space>
            <Button
              size="small"
              icon={topic.status === 'PINNED' ? <UnlockOutlined /> : <PushpinOutlined />}
              onClick={() => handleStatusChange(topic.status === 'PINNED' ? 'NORMAL' : 'PINNED')}
            >
              {topic.status === 'PINNED' ? '取消置顶' : '置顶'}
            </Button>
            <Button
              size="small"
              icon={topic.status === 'LOCKED' ? <UnlockOutlined /> : <LockOutlined />}
              onClick={() => handleStatusChange(topic.status === 'LOCKED' ? 'NORMAL' : 'LOCKED')}
            >
              {topic.status === 'LOCKED' ? '解除锁定' : '锁定'}
            </Button>
            <Button
              size="small"
              icon={topic.discussionEnabled ? <StopOutlined /> : <CheckCircleOutlined />}
              onClick={handleToggleDiscussion}
            >
              {topic.discussionEnabled ? '关闭讨论' : '开启讨论'}
            </Button>
            <Button
              size="small"
              icon={<EditOutlined />}
              onClick={() => {
                editForm.setFieldsValue({ title: topic.title, content: topic.content });
                setEditOpen(true);
              }}
            >
              编辑
            </Button>
            <Popconfirm title="确认删除该话题及所有回复？" onConfirm={handleDeleteTopic}>
              <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          </Space>
        </Space>
      </Card>

      {/* Replies */}
      <div style={{ marginBottom: 16 }}>
        <Title level={5}><CommentOutlined /> 回复 ({replies.length})</Title>
        {replies.length === 0 ? (
          <Empty description="暂无回复" />
        ) : (
          <List
            dataSource={replies}
            renderItem={(reply: TopicReply) => {
              const depth = getReplyDepth(reply.path || '');
              return (
                <div style={{ marginLeft: depth * 32, marginBottom: 8 }}>
                  <Card size="small" style={{ borderLeft: depth > 0 ? '3px solid #1890ff' : undefined }}>
                    <Space direction="vertical" size="small" style={{ width: '100%' }}>
                      <Space>
                        <Text strong>
                          {reply.isAnonymous ? '匿名用户' : (reply.authorName || `用户${reply.authorId}`)}
                        </Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {dayjs(reply.createTime).fromNow()}
                        </Text>
                      </Space>
                      <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
                        {reply.content}
                      </Paragraph>
                      <Space>
                        <Button type="link" size="small" onClick={() => handleReplyTo(reply)}>
                          回复
                        </Button>
                        <Popconfirm title="确认删除？" onConfirm={() => handleDeleteReply(reply.id)}>
                          <Button type="link" size="small" danger>删除</Button>
                        </Popconfirm>
                      </Space>
                    </Space>
                  </Card>
                </div>
              );
            }}
          />
        )}
      </div>

      {/* Reply Form */}
      {topic.discussionEnabled && topic.status !== 'LOCKED' ? (
        <Card>
          {replyParentId && (
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">
                正在回复 <Text strong>{replyParentAuthor}</Text>
                <Button
                  type="link" size="small"
                  onClick={() => { setReplyParentId(null); setReplyParentAuthor(''); }}
                >
                  取消
                </Button>
              </Text>
            </div>
          )}
          <TextArea
            id="reply-textarea"
            rows={4}
            value={replyContent}
            onChange={(e) => setReplyContent(e.target.value)}
            placeholder="输入你的回复..."
            style={{ marginBottom: 12 }}
          />
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space>
              <Text type="secondary">匿名回复：</Text>
              <Switch
                checked={replyAnonymous}
                onChange={setReplyAnonymous}
                checkedChildren="匿名"
                unCheckedChildren="实名"
              />
            </Space>
            <Button type="primary" loading={submitting} onClick={handleReply} icon={<CommentOutlined />}>
              发表回复
            </Button>
          </Space>
        </Card>
      ) : (
        <Card>
          <Text type="secondary">
            {topic.status === 'LOCKED' ? '该话题已锁定，无法回复' : '该话题讨论已关闭'}
          </Text>
        </Card>
      )}

      {/* Edit Topic Modal */}
      <Modal
        title="编辑话题"
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        footer={null}
        destroyOnClose
        width={560}
      >
        <Form form={editForm} layout="vertical" onFinish={handleEditTopic}>
          <Form.Item name="title" label="标题" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="content" label="内容" rules={[{ required: true }]}>
            <Input.TextArea rows={5} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>保存</Button>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
