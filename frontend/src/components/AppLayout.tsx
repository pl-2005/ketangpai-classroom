import { useState, useEffect, useRef } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Avatar, Dropdown, Space, Typography, theme, App, Badge, Modal, Form, Input } from 'antd';
import {
  BookOutlined,
  BellOutlined,
  EditOutlined,
  LogoutOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  CameraOutlined,
  LockOutlined,
} from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';
import { userApi, notificationsApi } from '../api';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

interface MenuItem {
  key: string;
  icon: React.ReactNode;
  label: string;
  path: string;
}

interface ProfileFormValues {
  username: string;
  realName?: string;
  email?: string;
}

interface PasswordFormValues {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const menuItems: MenuItem[] = [
  { key: 'courses', icon: <BookOutlined />, label: '我的课程', path: '/courses' },
  { key: 'notifications', icon: <BellOutlined />, label: '消息中心', path: '/notifications' },
  { key: 'drafts', icon: <EditOutlined />, label: '备课区', path: '/drafts' },
];

export default function AppLayout() {
  const { message } = App.useApp();
  const [collapsed, setCollapsed] = useState(false);
  const { user, logout, refreshUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { token: themeToken } = theme.useToken();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  const [profileOpen, setProfileOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [profileSaving, setProfileSaving] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [profileForm] = Form.useForm<ProfileFormValues>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();

  // 页面加载时获取头像预签名 URL
  useEffect(() => {
    const fetchAvatar = async () => {
      try {
        const data = await userApi.getAvatarUrl() as unknown as { avatarUrl: string };
        if (data?.avatarUrl) {
          setAvatarUrl(data.avatarUrl);
        }
      } catch {
        // 用户可能未设置头像，忽略
      }
    };
    fetchAvatar();
  }, [user?.id]);

  // 定期轮询未读消息数（每 30 秒），监听到已读事件时立即刷新
  useEffect(() => {
    const fetchUnread = async () => {
      try {
        const data = await notificationsApi.getUnreadCount() as unknown as { count: number };
        setUnreadCount(data?.count ?? 0);
      } catch {
        // 静默处理
      }
    };
    fetchUnread();
    const timer = setInterval(fetchUnread, 30000);
    const onUpdated = () => fetchUnread();
    window.addEventListener('notifications-updated', onUpdated);
    return () => {
      clearInterval(timer);
      window.removeEventListener('notifications-updated', onUpdated);
    };
  }, []);

  const visibleMenuItems = user?.role === 'TEACHER'
    ? menuItems
    : menuItems.filter((item) => item.key !== 'drafts');

  // 根据当前路径确定选中菜单
  const selectedKey = visibleMenuItems.find((item) =>
    location.pathname.startsWith(item.path)
  )?.key || 'courses';

  const handleMenuClick = (info: { key: string }) => {
    const item = visibleMenuItems.find((m) => m.key === info.key);
    if (item) navigate(item.path);
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // 头像上传
  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // 前端校验文件类型和大小
    const allowedTypes = ['image/png', 'image/jpeg', 'image/gif'];
    if (!allowedTypes.includes(file.type)) {
      message.error('头像仅支持 PNG、JPG、GIF 格式');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      message.error('头像大小不能超过 5MB');
      return;
    }

    try {
      const data = await userApi.uploadAvatar(file) as unknown as { avatarUrl: string };
      setAvatarUrl(data.avatarUrl);
      await refreshUser();
      message.success('头像更新成功');
    } catch {
      message.error('头像上传失败');
    } finally {
      // 重置 input 以允许重新选择同一文件
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleEditProfileClick = () => {
    profileForm.setFieldsValue({
      username: user?.username || '',
      realName: user?.realName || '',
      email: user?.email || '',
    });
    setProfileOpen(true);
  };

  const handleSaveProfile = async () => {
    try {
      const values = await profileForm.validateFields();
      setProfileSaving(true);
      await userApi.updateProfile({
        realName: values.realName?.trim() || '',
        email: values.email?.trim() || '',
      });
      await refreshUser();
      setProfileOpen(false);
      message.success('资料已更新');
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.message || '资料更新失败');
    } finally {
      setProfileSaving(false);
    }
  };

  const handleChangePasswordClick = () => {
    passwordForm.resetFields();
    setPasswordOpen(true);
  };

  const handleSavePassword = async () => {
    try {
      const values = await passwordForm.validateFields();
      setPasswordSaving(true);
      await userApi.updatePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      setPasswordOpen(false);
      message.success('密码已更新，请重新登录');
      logout();
      navigate('/login');
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.message || '密码修改失败');
    } finally {
      setPasswordSaving(false);
    }
  };

  const userMenuItems = [
    {
      key: 'role',
      label: `角色：${user?.role === 'TEACHER' ? '教师' : '学生'}`,
      disabled: true,
    },
    { type: 'divider' as const },
    {
      key: 'profile',
      label: '编辑资料',
      icon: <UserOutlined />,
    },
    {
      key: 'password',
      label: '修改密码',
      icon: <LockOutlined />,
    },
    {
      key: 'avatar',
      label: '更换头像',
      icon: <CameraOutlined />,
    },
    { type: 'divider' as const },
    {
      key: 'logout',
      label: '退出登录',
      icon: <LogoutOutlined />,
      danger: true,
    },
  ];

  const handleUserMenuClick = (info: { key: string }) => {
    switch (info.key) {
      case 'logout':
        handleLogout();
        break;
      case 'avatar':
        handleAvatarClick();
        break;
      case 'profile':
        handleEditProfileClick();
        break;
      case 'password':
        handleChangePasswordClick();
        break;
    }
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* 隐藏的文件选择器 */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/png,image/jpeg,image/gif"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        theme="light"
        style={{
          borderRight: `1px solid ${themeToken.colorBorderSecondary}`,
        }}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderBottom: `1px solid ${themeToken.colorBorderSecondary}`,
          }}
        >
          <Text strong style={{ fontSize: collapsed ? 14 : 18, whiteSpace: 'nowrap' }}>
            {collapsed ? '课堂' : 'AI 课堂派'}
          </Text>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          onClick={handleMenuClick}
          items={visibleMenuItems.map((item) => ({
            key: item.key,
            icon: item.key === 'notifications' && unreadCount > 0
              ? <Badge dot offset={[-2, 4]}>{item.icon}</Badge>
              : item.icon,
            label: item.label,
          }))}
          style={{ border: 'none' }}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            padding: '0 24px',
            background: themeToken.colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: `1px solid ${themeToken.colorBorderSecondary}`,
          }}
        >
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <Dropdown menu={{ items: userMenuItems, onClick: handleUserMenuClick }}>
            <Space style={{ cursor: 'pointer' }} title="用户菜单">
              <Avatar
                src={avatarUrl}
                icon={!avatarUrl ? <UserOutlined /> : undefined}
                size="small"
              />
              <Text>{user?.realName || user?.username || '用户'}</Text>
            </Space>
          </Dropdown>
        </Header>
        <Content
          style={{
            margin: 24,
            padding: 24,
            background: themeToken.colorBgContainer,
            borderRadius: themeToken.borderRadiusLG,
            minHeight: 280,
            overflow: 'auto',
          }}
        >
          <Outlet />
        </Content>
      </Layout>
      <Modal
        title="编辑资料"
        open={profileOpen}
        onOk={handleSaveProfile}
        onCancel={() => setProfileOpen(false)}
        confirmLoading={profileSaving}
        okText="保存"
        cancelText="取消"
      >
        <Form form={profileForm} layout="vertical">
          <Form.Item label="用户名" name="username">
            <Input disabled />
          </Form.Item>
          <Form.Item
            label="姓名"
            name="realName"
            rules={[{ max: 50, message: '姓名长度不能超过 50 个字符' }]}
          >
            <Input maxLength={50} placeholder="请输入姓名" />
          </Form.Item>
          <Form.Item
            label="邮箱"
            name="email"
            rules={[
              { type: 'email', message: '邮箱格式不正确' },
              { max: 100, message: '邮箱长度不能超过 100 个字符' },
            ]}
          >
            <Input maxLength={100} placeholder="请输入邮箱" />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="修改密码"
        open={passwordOpen}
        onOk={handleSavePassword}
        onCancel={() => setPasswordOpen(false)}
        confirmLoading={passwordSaving}
        okText="保存"
        cancelText="取消"
      >
        <Form form={passwordForm} layout="vertical">
          <Form.Item
            label="原密码"
            name="oldPassword"
            rules={[{ required: true, message: '请输入原密码' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[
              { required: true, message: '请输入新密码' },
              {
                pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$/,
                message: '新密码须为 8-32 个字符，且包含大写字母、小写字母和数字',
              },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="确认新密码"
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的新密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  );
}
