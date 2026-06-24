import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { App, Button, Card, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { login } from '@/api/auth'
import { errorMessage } from '@/api/client'
import { getToken, saveSession } from '@/auth/session'

interface LoginForm {
  username: string
  password: string
}

export default function LoginPage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const [submitting, setSubmitting] = useState(false)

  if (getToken()) {
    return <Navigate to="/courses" replace />
  }

  async function submit(values: LoginForm) {
    setSubmitting(true)
    try {
      const session = await login(values.username, values.password)
      saveSession(session)
      message.success('登录成功')
      navigate('/courses', { replace: true })
    } catch (error) {
      message.error(errorMessage(error, '登录失败'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-visual">
        <div className="login-emblem">课</div>
        <Typography.Title>把课程管理变得清楚、简单</Typography.Title>
        <Typography.Paragraph>
          课程、作业、资料与讨论集中在一个工作台中。
        </Typography.Paragraph>
      </div>
      <Card className="login-card" variant="borderless">
        <Typography.Title level={2}>欢迎回来</Typography.Title>
        <Typography.Paragraph type="secondary">登录智能课堂管理系统</Typography.Paragraph>
        <Form<LoginForm> layout="vertical" onFinish={submit} requiredMark={false} size="large">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} autoComplete="username" placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password
              prefix={<LockOutlined />}
              autoComplete="current-password"
              placeholder="请输入密码"
            />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>
            登录
          </Button>
        </Form>
      </Card>
    </div>
  )
}
