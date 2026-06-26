import { useState, useEffect, useRef } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Avatar, Dropdown, Space, Typography, theme, message } from 'antd';
import {
  BookOutlined,
  BellOutlined,
  FileTextOutlined,
  CommentOutlined,
  FolderOutlined,
  EditOutlined,
  RobotOutlined,
  LogoutOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  CameraOutlined,
} from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';
import { userApi } from '../api';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

interface MenuItem {
  key: string;
  icon: React.ReactNode;
  label: string;
  path: string;
}

const menuItems: MenuItem[] = [
  { key: 'courses', icon: <BookOutlined />, label: '我的课程', path: '/courses' },
  { key: 'notifications', icon: <BellOutlined />, label: '消息中心', path: '/notifications' },
  { key: 'drafts', icon: <EditOutlined />, label: '备课区', path: '/drafts' },
];

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const { user, logout, refreshUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { token: themeToken } = theme.useToken();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);

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

  // 根据当前路径确定选中菜单
  const selectedKey = menuItems.find((item) =>
    location.pathname.startsWith(item.path)
  )?.key || 'courses';

  const handleMenuClick = (info: { key: string }) => {
    const item = menuItems.find((m) => m.key === info.key);
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

  const userMenuItems = [
    {
      key: 'role',
      label: `角色：${user?.role === 'TEACHER' ? '教师' : '学生'}`,
      disabled: true,
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
          items={menuItems.map((item) => ({
            key: item.key,
            icon: item.icon,
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
            <Space style={{ cursor: 'pointer' }} title="点击更换头像">
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
    </Layout>
  );
}
