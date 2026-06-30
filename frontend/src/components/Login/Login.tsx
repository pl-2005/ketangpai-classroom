import { useState } from 'react';
import { Form, Input, Button, Checkbox, App } from 'antd';
import { MailOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../api';
import { useAuth } from '../../contexts/AuthContext';
import styles from './Login.module.css';
import bgImage from '@/assets/login/bg.png';

const Login = () => {
    const { message } = App.useApp();
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();

    const onFinish = async (values: Record<string, string>) => {
        setLoading(true);
        try {
            const { username, password } = values;
            const result: any = await authApi.login({ username, password });
            if (result.token) {
                login(result.token, result.user);
                message.success('登录成功');
                navigate('/', { replace: true });
            }
        } catch (error) {
            console.error('登录失败:', error);
            message.error('登录失败，请检查账号密码');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={styles.loginContainer}>
            <div className={styles.leftSection}>
                <div className={styles.logo}>
                    <span className={styles.logoText}>AI课堂派</span>
                </div>
                <div className={styles.illustration}>
                    <img
                        src={bgImage}
                        alt="教育插画"
                        className={styles.illustrationImg}
                    />
                </div>
            </div>

            <div className={styles.rightSection}>
                <div className={styles.loginBox}>
                    <h2 className={styles.title}>账号登录</h2>

                    <Form
                        name="login"
                        onFinish={onFinish}
                        className={styles.form}
                        layout="vertical"
                    >
                        <Form.Item
                            name="username"
                            label="账号"
                            rules={[
                                { required: true, message: '请输入邮箱/手机号/账号' },
                            ]}
                        >
                            <Input
                                prefix={<MailOutlined />}
                                placeholder="请输入邮箱/手机号/账号"
                                className={styles.input}
                            />
                        </Form.Item>

                        <Form.Item
                            name="password"
                            label="密码"
                            rules={[
                                { required: true, message: '请输入密码' },
                                { min: 6, message: '密码长度至少6位' },
                            ]}
                        >
                            <Input.Password
                                prefix={<LockOutlined />}
                                placeholder="请输入密码"
                                className={styles.input}
                            />
                        </Form.Item>

                        <Form.Item className={styles.formItem}>
                            <div className={styles.rememberForgot}>
                                <Checkbox>下次自动登录</Checkbox>
                                <a href="#" className={styles.forgotLink}>忘记密码?</a>
                            </div>
                        </Form.Item>

                        <Form.Item className={styles.formItem}>
                            <Button
                                type="primary"
                                htmlType="submit"
                                loading={loading}
                                className={styles.loginBtn}
                                block
                            >
                                登录
                            </Button>
                        </Form.Item>
                    </Form>

                    <div className={styles.registerLink}>
                        还没有账号？<a href="/register">去注册</a>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Login;