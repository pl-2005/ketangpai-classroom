import { useState } from 'react';
import { Form, Input, Button, Radio, message } from 'antd';
import { MailOutlined, LockOutlined, UserOutlined } from '@ant-design/icons';
import { authApi } from '../../api/auth/auth-api';
import styles from './Register.module.css';
import bgImage from '@/assets/login/bg.png';

const Register = () => {
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: Record<string, string>) => {
    const { username, email, password, confirmPassword, identity, realName } = values;

    if (password !== confirmPassword) {
      message.error('两次输入的密码不一致');
      return;
    }

    setLoading(true);
    try {
      const result = await authApi.register({
        username,
        email,
        password,
        realName,
        role: identity === 'teacher' ? 'TEACHER' : 'STUDENT',
      });

      if (result) {
        message.success('注册成功，请登录');
        window.location.href = '/login';
      }
    } catch (error: any) {
      const msg = error?.response?.data?.message || '注册失败，请稍后重试';
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.registerContainer}>
      <div className={styles.leftSection}>
        <div className={styles.logo}>
          <span className={styles.logoText}>AI课堂派</span>
        </div>
        <div className={styles.illustration}>
          <img
            src={bgImage}
            className={styles.illustrationImg}
          />
        </div>
      </div>

      <div className={styles.rightSection}>
        <div className={styles.registerBox}>
          <h2 className={styles.title}>注册账号</h2>

          <Form
            name="register"
            onFinish={onFinish}
            className={styles.form}
            layout="vertical"
          >
            <Form.Item
              name="username"
              label="用户名"
              rules={[
                { required: true, message: '请输入用户名' },
                { min: 4, max: 50, message: '用户名长度应在4-50之间' },
                { pattern: /^[A-Za-z][A-Za-z0-9_]*$/, message: '用户名须以字母开头，只能包含字母、数字和下划线' },
              ]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="请输入用户名"
                className={styles.input}
              />
            </Form.Item>

            <Form.Item
              name="email"
              label="邮箱"
              rules={[
                { required: false, message: '请输入邮箱' },
                {
                  validator: (_, value) => {
                    if (!value) return Promise.resolve();
                    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                    if (!emailRegex.test(value)) {
                      return Promise.reject(new Error('请输入正确的邮箱'));
                    }
                    return Promise.resolve();
                  },
                },
              ]}
            >
              <Input
                prefix={<MailOutlined />}
                placeholder="请输入邮箱"
                className={styles.input}
              />
            </Form.Item>

            <Form.Item
              name="password"
              label="密码"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 8, message: '密码长度至少8位' },
                { max: 32, message: '密码长度不能超过32位' },
                {
                  validator: (_, value) => {
                    if (!value) return Promise.resolve();
                    const hasUpper = /[A-Z]/.test(value);
                    const hasLower = /[a-z]/.test(value);
                    const hasNumber = /[0-9]/.test(value);
                    if (!hasUpper || !hasLower || !hasNumber) {
                      return Promise.reject(new Error('密码须包含大写字母、小写字母和数字'));
                    }
                    return Promise.resolve();
                  },
                },
              ]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="请输入密码"
                className={styles.input}
              />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              label="确认密码"
              rules={[
                { required: true, message: '请再次输入密码确认' },
              ]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="请再次输入密码确认"
                className={styles.input}
              />
            </Form.Item>

            <Form.Item className={styles.identitySection}>
              <span className={styles.sectionLabel}>选择身份</span>
              <Form.Item
                name="identity"
                rules={[{ required: true, message: '请选择身份' }]}
                style={{ marginBottom: 0 }}
              >
                <Radio.Group className={styles.radioGroup}>
                  <Radio.Button value="teacher" className={styles.radioBtn}>
                    <UserOutlined className={styles.radioIcon} />
                    老师
                  </Radio.Button>
                  <Radio.Button value="student" className={styles.radioBtn}>
                    <UserOutlined className={styles.radioIcon} />
                    学生
                  </Radio.Button>
                </Radio.Group>
              </Form.Item>
            </Form.Item>

            <Form.Item
              name="realName"
              label="姓名"
              rules={[
                { required: false, message: '请输入姓名' },
                { min: 2, max: 20, message: '姓名长度应在2-20之间' },
              ]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="请输入姓名"
                className={styles.input}
              />
            </Form.Item>

            <Form.Item className={styles.formItem}>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                className={styles.registerBtn}
                block
              >
                注册
              </Button>
            </Form.Item>
          </Form>

          <div className={styles.loginLink}>
            已有账号？<a href="/login">去登录</a>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Register;