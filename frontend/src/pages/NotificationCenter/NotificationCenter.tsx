import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Tag, Button, Select, Space, Typography, Empty, Spin, App, Popconfirm } from 'antd';
import { BellOutlined, CheckOutlined, DeleteOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { notificationsApi, type Notification, type NotificationType } from '../../api';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

const { Text } = Typography;

const TYPE_LABELS: Record<NotificationType, string> = {
  ASSIGNMENT_PUBLISHED: '作业发布',
  ASSIGNMENT_URGED: '催交通知',
  ASSIGNMENT_GRADED: '评分通知',
  ASSIGNMENT_RETURNED: '退回通知',
  TOPIC_REPLY: '话题回复',
  COURSE_JOINED: '加入课程',
  COURSE_ANNOUNCEMENT: '课程公告',
};

const TYPE_COLORS: Record<NotificationType, string> = {
  ASSIGNMENT_PUBLISHED: 'blue',
  ASSIGNMENT_URGED: 'orange',
  ASSIGNMENT_GRADED: 'green',
  ASSIGNMENT_RETURNED: 'purple',
  TOPIC_REPLY: 'cyan',
  COURSE_JOINED: 'geekblue',
  COURSE_ANNOUNCEMENT: 'magenta',
};

export default function NotificationCenter() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [typeFilter, setTypeFilter] = useState<NotificationType | undefined>(undefined);

  const fetchNotifications = useCallback(async (page: number, size: number, type?: NotificationType) => {
    setLoading(true);
    try {
      const data = await notificationsApi.getNotifications({
        page: page - 1,
        size,
        type,
      }) as unknown as {
        content: Notification[];
        totalElements: number;
      };
      setNotifications(data.content || []);
      setPagination({ current: page, pageSize: size, total: data.totalElements || 0 });
    } catch {
      message.error('获取通知列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchNotifications(1, pagination.pageSize, typeFilter);
  }, [typeFilter, fetchNotifications, pagination.pageSize]);

  const handleTableChange = (pag: { current?: number; pageSize?: number }) => {
    fetchNotifications(pag.current || 1, pag.pageSize || 20, typeFilter);
  };

  const handleMarkRead = async (id: number) => {
    try {
      await notificationsApi.markAsRead(id);
      message.success('已标记为已读');
      fetchNotifications(pagination.current, pagination.pageSize, typeFilter);
    } catch {
      message.error('标记已读失败');
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await notificationsApi.markAllAsRead();
      message.success('已全部标记为已读');
      fetchNotifications(1, pagination.pageSize, typeFilter);
    } catch {
      message.error('全部标记已读失败');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await notificationsApi.deleteNotification(id);
      message.success('已删除');
      fetchNotifications(pagination.current, pagination.pageSize, typeFilter);
    } catch {
      message.error('删除通知失败');
    }
  };

  const handleNotificationClick = (notification: Notification) => {
    const { type, courseId, relatedId } = notification;

    // 自动标记为已读
    if (!notification.isRead) {
      handleMarkRead(notification.id);
    }

    // 根据类型跳转
    if (type === 'TOPIC_REPLY' && courseId && relatedId) {
      navigate(`/courses/${courseId}/topics/${relatedId}`);
    } else if (['ASSIGNMENT_PUBLISHED', 'ASSIGNMENT_URGED'].includes(type) && courseId && relatedId) {
      navigate(`/courses/${courseId}/assignments/${relatedId}`);
    } else if (['ASSIGNMENT_GRADED', 'ASSIGNMENT_RETURNED'].includes(type) && courseId) {
      navigate(`/courses/${courseId}`);
    } else if (courseId) {
      navigate(`/courses/${courseId}`);
    }
  };

  const columns = [
    {
      title: '类型',
      dataIndex: 'type',
      width: 100,
      render: (type: NotificationType) => (
        <Tag color={TYPE_COLORS[type]}>{TYPE_LABELS[type] || type}</Tag>
      ),
    },
    {
      title: '内容',
      key: 'content',
      render: (_: unknown, record: Notification) => (
        <div style={{ cursor: 'pointer' }} onClick={() => handleNotificationClick(record)}>
          <Text strong={!record.isRead}>{record.title}</Text>
          <br />
          <Text type="secondary" ellipsis={{ tooltip: record.content }}>
            {record.content}
          </Text>
        </div>
      ),
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 160,
      render: (time: string) => <Text type="secondary">{dayjs(time).fromNow()}</Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_: unknown, record: Notification) => (
        <Space>
          {!record.isRead && (
            <Button type="link" size="small" icon={<CheckOutlined />} onClick={() => handleMarkRead(record.id)}>
              标记已读
            </Button>
          )}
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Space>
          <BellOutlined style={{ fontSize: 18 }} />
          <Text strong style={{ fontSize: 18 }}>消息中心</Text>
        </Space>
        <Space>
          <Select
            allowClear
            placeholder="筛选通知类型"
            style={{ width: 160 }}
            value={typeFilter}
            onChange={(value) => setTypeFilter(value)}
            options={Object.entries(TYPE_LABELS).map(([value, label]) => ({ value, label }))}
          />
          <Button icon={<CheckOutlined />} onClick={handleMarkAllRead}>
            全部已读
          </Button>
        </Space>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>
      ) : notifications.length === 0 ? (
        <Empty description="暂无通知" />
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={notifications}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
          onChange={handleTableChange}
          rowClassName={(record) => record.isRead ? '' : 'notification-unread-row'}
        />
      )}

      <style>{`
        .notification-unread-row td { font-weight: 500; }
      `}</style>
    </div>
  );
}
