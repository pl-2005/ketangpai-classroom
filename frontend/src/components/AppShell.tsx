import {
  AppstoreOutlined,
  DeleteOutlined,
  InboxOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Avatar, Button, Layout, Menu, Space, Tag, Typography } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { clearSession, getCurrentUser } from '@/auth/session'

const { Header, Content } = Layout

export default function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const user = getCurrentUser()

  const selectedKey = location.pathname.startsWith('/courses/trash')
    ? '/courses/trash'
    : location.pathname.startsWith('/courses/archived')
      ? '/courses/archived'
      : '/courses'

  function logout() {
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <Layout className="app-layout">
      <Header className="app-header">
        <div className="brand" onClick={() => navigate('/courses')}>
          <div className="brand-mark">课</div>
          <div>
            <Typography.Text className="brand-name">智能课堂</Typography.Text>
            <Typography.Text className="brand-subtitle">课程管理中心</Typography.Text>
          </div>
        </div>

        <Menu
          className="main-nav"
          mode="horizontal"
          selectedKeys={[selectedKey]}
          items={[
            { key: '/courses', icon: <AppstoreOutlined />, label: '我的课程' },
            { key: '/courses/archived', icon: <InboxOutlined />, label: '归档课程' },
            ...(user?.role === 'TEACHER'
              ? [{ key: '/courses/trash', icon: <DeleteOutlined />, label: '回收站' }]
              : []),
          ]}
          onClick={({ key }) => navigate(key)}
        />

        <Space className="user-panel" size={10}>
          <Avatar src={user?.avatarUrl} icon={<UserOutlined />} />
          <div className="user-copy">
            <Typography.Text>{user?.realName || user?.username}</Typography.Text>
            <Tag color={user?.role === 'TEACHER' ? 'blue' : 'green'}>
              {user?.role === 'TEACHER' ? '教师' : '学生'}
            </Tag>
          </div>
          <Button type="text" icon={<LogoutOutlined />} onClick={logout} aria-label="退出登录" />
        </Space>
      </Header>
      <Content className="app-content">
        <Outlet />
      </Content>
    </Layout>
  )
}
