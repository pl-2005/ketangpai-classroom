import { useState, useEffect } from 'react';
import { Layout, Breadcrumb, Tabs, Card, Button, Input, Modal, message, Upload, Spin, Avatar, Form, Row, Col, Space, Typography } from 'antd';
import { HomeOutlined, BookOutlined, UserOutlined, KeyOutlined, MailOutlined, PlusOutlined, LoadingOutlined } from '@ant-design/icons';
import type { RcFile, UploadProps } from 'antd/es/upload/interface';
import type { FormInstance } from 'antd';
import { authApi, userApi } from '../../api';
import type { User } from '../../api/auth/auth-api';
import styles from './UserSettings.module.css';

const { Header, Content } = Layout;
const { Title, Text, Paragraph } = Typography;

// 本地预览base64工具（官方示例逻辑）
const getBase64 = (img: RcFile, callback: (url: string) => void) => {
  const reader = new FileReader();
  reader.addEventListener('load', () => callback(reader.result as string));
  reader.readAsDataURL(img);
};

const UserSettings = () => {
  const [activeTab, setActiveTab] = useState('account');
  const [userInfo, setUserInfo] = useState<User | null>({
    id: 0,
    username: 'ktpbbb',
    email: '3137239514@qq.com',
    realName: 'xld',
    role: 'TEACHER',
    avatarUrl: '',
  });
  const [loading, setLoading] = useState(true);
  // 上传加载状态
  const [avatarUploadLoading, setAvatarUploadLoading] = useState(false);
  const [previewImage, setPreviewImage] = useState<string>('');
  const [avatarModalVisible, setAvatarModalVisible] = useState(false);
  const [passwordModalVisible, setPasswordModalVisible] = useState(false);
  const [nameModalVisible, setNameModalVisible] = useState(false);
  const [emailModalVisible, setEmailModalVisible] = useState(false);

  // Form 实例
  const [passwordForm] = Form.useForm();
  const [nameForm] = Form.useForm();
  const [emailForm] = Form.useForm();

  useEffect(() => {
    fetchUserInfo();
  }, []);

  const fetchUserInfo = async () => {
    setLoading(true);
    try {
      const user = await authApi.getCurrentUser();
      setUserInfo(user.data);
      setPreviewImage(user.data.avatarUrl || '');
    } catch (error) {
      message.error('获取用户信息失败');
      console.error('Failed to fetch user info:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleUpdatePassword = async () => {
    try {
      const values = await passwordForm.validateFields();
      await userApi.updatePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      message.success('密码修改成功');
      setPasswordModalVisible(false);
      passwordForm.resetFields();
    } catch (error) {
      if (error instanceof Error && !('errorFields' in error)) {
        message.error('密码修改失败');
      }
    }
  };

  const handleUpdateName = async () => {
    try {
      const values = await nameForm.validateFields();
      await userApi.updateProfile({
        realName: values.realName.trim(),
        email: userInfo?.email || '',
      });
      setUserInfo(prev => prev ? {
        ...prev,
        realName: values.realName.trim()
      } : null);
      message.success('姓名修改成功');
      setNameModalVisible(false);
      nameForm.resetFields();
    } catch (error) {
      if (error instanceof Error && !('errorFields' in error)) {
        message.error('姓名修改失败');
      }
    }
  };

  const handleUpdateEmail = async () => {
    try {
      const values = await emailForm.validateFields();
      await userApi.updateProfile({
        email: values.email.trim(),
        realName: userInfo?.realName || '',
      });
      setUserInfo(prev => prev ? {
        ...prev,
        email: values.email.trim()
      } : null);
      message.success('邮箱修改成功');
      setEmailModalVisible(false);
      emailForm.resetFields();
    } catch (error) {
      if (error instanceof Error && !('errorFields' in error)) {
        message.error('邮箱修改失败');
      }
    }
  };

  // 上传前校验
  const avatarBeforeUpload = (file: RcFile) => {
    const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png';
    if (!isJpgOrPng) {
      message.error('仅支持 JPG / PNG 图片！');
      return false;
    }
    const isLt2M = file.size / 1024 / 1024 < 2;
    if (!isLt2M) {
      message.error('图片大小不能超过2MB！');
      return false;
    }
    // 选中文件立刻本地预览
    getBase64(file, (url) => setPreviewImage(url));
    return true;
  };

  // 上传回调（适配 /api/user/avatar 后端返回结构）
  const avatarHandleChange: UploadProps['onChange'] = (info) => {
    if (info.file.status === 'uploading') {
      setAvatarUploadLoading(true);
      return;
    }
    if (info.file.status === 'done') {
      setAvatarUploadLoading(false);
      // 适配原有后端返回：{ data: { avatarUrl: "xxx" } }
      const res = info.file.response;
      if (res?.data?.avatarUrl) {
        setPreviewImage(res.data.avatar);
        setUserInfo(prev => prev ? { ...prev, avatarUrl: res.data.avatarUrl } : null);
        message.success('头像上传成功');
      }
    }
    if (info.file.status === 'error') {
      setAvatarUploadLoading(false);
      message.error('头像上传失败，请重试');
    }
  };

  const handleAvatarClick = () => {
    setPreviewImage(userInfo?.avatarUrl || '');
    setAvatarModalVisible(true);
  };

  const handleAvatarConfirm = () => {
    if (!previewImage) {
      message.error('请先上传头像');
      return;
    }
    message.success('头像设置成功');
    setAvatarModalVisible(false);
  };

  // 上传按钮模板
  const uploadBtn = (
    <div className={styles.uploadBtnWrap}>
      {avatarUploadLoading ? <LoadingOutlined /> : <PlusOutlined />}
      <div className={styles.uploadText}>点击上传</div>
    </div>
  );

  const uploadProps: UploadProps = {
    name: 'file',
    action: '/api/user/avatar', // 恢复你业务真实接口
    beforeUpload: avatarBeforeUpload,
    onChange: avatarHandleChange,
    listType: 'picture-card',
    showUploadList: false,
    headers: {
      Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
    },
  };

  // 面包屑配置项（替代 Breadcrumb.Item）
  const breadcrumbItems = [
    {
      title: (
        <a href="/courses">
          <HomeOutlined />
          <span>首页</span>
        </a>
      ),
    },
    {
      title: (
        <a href="/courses">
          <BookOutlined />
          <span>我的课堂</span>
        </a>
      ),
    },
    {
      title: (
        <>
          <UserOutlined />
          <span>用户设置</span>
        </>
      ),
    },
  ];

  // Tabs 配置项（替代 Tabs.TabPane）
  const tabsItems = [
    {
      key: 'account',
      label: '账户信息',
      children: (
        <Card className={styles.settingCard}>
          <h3 className={styles.sectionTitle}>账号设置</h3>
          <div className={styles.settingItem}>
            <span className={styles.label}>账号</span>
            <span className={styles.value}>{userInfo?.username || '未设置'}</span>
          </div>
          <div className={styles.settingItem}>
            <span className={styles.label}>密码</span>
            <Button type="link" className={styles.actionBtn} onClick={() => setPasswordModalVisible(true)}>修改密码</Button>
          </div>

          <h3 className={styles.sectionTitle}>基础信息</h3>
          <div className={styles.settingItem}>
            <span className={styles.label}>用户名</span>
            <span className={styles.value}>{userInfo?.username || '未设置'}</span>
          </div>
          <div className={styles.settingItem}>
            <span className={styles.label}>姓名</span>
            <div className={styles.valueWithAction}>
              <span className={styles.value}>{userInfo?.realName || '未设置'}</span>
              <Button type="link" className={styles.actionBtn} onClick={() => setNameModalVisible(true)}>修改姓名</Button>
            </div>
          </div>
          <div className={styles.settingItem}>
            <span className={styles.label}>邮箱</span>
            <div className={styles.valueWithAction}>
              <span>{userInfo?.email || '未绑定'}</span>
              <Button type="link" className={styles.actionBtn} onClick={() => setEmailModalVisible(true)}>绑定邮箱</Button>
            </div>
          </div>
          <div className={styles.settingItem}>
            <span className={styles.label}>角色</span>
            <span className={styles.value}>{userInfo?.role === 'TEACHER' ? '教师' : '学生'}</span>
          </div>
        </Card>
      ),
    },
  ];

  const roleText = userInfo?.role === 'TEACHER' ? '教师' : '学生';

  if (loading) {
    return (
      <Layout className={styles.layout}>
        <Content className={styles.content}>
          <div className={styles.loadingContainer}>
            <Spin size="large" />
          </div>
        </Content>
      </Layout>
    );
  }

  return (
    <Layout className={styles.layout}>
      <Header className={styles.header}>
        <div className={styles.headerContent}>
          <div className={styles.headerLeft}>
            {/* 重构面包屑：使用 items 配置 */}
            <Breadcrumb className={styles.breadcrumb} items={breadcrumbItems} />
          </div>
        </div>
      </Header>

      <Content className={styles.content}>
        <Card style={{ width: '60%', margin: '0 auto' }}>
          {/* 头像区域：头像+右侧姓名 */}
          <div className={styles.userAvatar}>
            <div className={styles.avatarWrapper} onClick={handleAvatarClick}>
              {userInfo?.avatarUrl ? (
                <Avatar size={100} src={userInfo.avatarUrl} className={styles.avatarImg} />
              ) : (
                <Avatar size={100} icon={<UserOutlined />} />
              )}
            </div>
            <div className={styles.avatarNameWrapper}>
              <h2 className={styles.avatarName}>{userInfo?.realName || userInfo?.username || '未设置'}</h2>
              <p className={styles.avatarRole}>{roleText}</p>
            </div>
          </div>

          {/* 重构Tabs：使用 items 配置 */}
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            className={styles.tabs}
            items={tabsItems}
          />
        </Card>
      </Content>

      {/* 修改密码弹窗 */}
      <Modal
        title="修改密码"
        open={passwordModalVisible}
        onCancel={() => {
          setPasswordModalVisible(false);
          passwordForm.resetFields();
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setPasswordModalVisible(false);
            passwordForm.resetFields();
          }}>取消</Button>,
          <Button key="confirm" type="primary" onClick={handleUpdatePassword}>确定</Button>,
        ]}
      >
        <Form form={passwordForm} layout="vertical" className={styles.passwordForm}>

          <Form.Item
            label="旧密码"
            name="oldPassword"
            rules={[{ required: true, message: '请输入旧密码' }, { min: 6, message: '密码长度不能少于6位' }]}
          >
            <Input.Password
              placeholder="请输入旧密码"
              prefix={<KeyOutlined />}
              autoComplete="current-password"
            />
          </Form.Item>
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[{ required: true, message: '请输入新密码' }, { min: 6, message: '新密码长度不能少于6位' }]}
          >
            <Input.Password
              placeholder="请输入新密码"
              prefix={<KeyOutlined />}
              autoComplete="new-password"
            />
          </Form.Item>
          <Form.Item
            label="确认密码"
            name="confirmPassword"
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) return Promise.resolve();
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password
              placeholder="请再次输入新密码"
              prefix={<KeyOutlined />}
              autoComplete="new-password"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 设置头像弹窗 */}
      <Modal
        title="设置头像"
        open={avatarModalVisible}
        onCancel={() => {
          setAvatarModalVisible(false);
          setPreviewImage(userInfo?.avatarUrl || '');
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setAvatarModalVisible(false);
            setPreviewImage(userInfo?.avatarUrl || '');
          }}>取消</Button>,
          <Button key="confirm" type="primary" onClick={handleAvatarConfirm}>确定</Button>,
        ]}
        width={520}
      >
        <Row className={styles.avatarModalContent} gutter={24}>
          <Col flex={1} className={styles.uploadArea}>
            <Upload {...uploadProps} className={styles.uploadCard} style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {previewImage ? (
                <img draggable={false} src={previewImage} alt="头像预览" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : uploadBtn}
            </Upload>
            <Paragraph className={styles.uploadTip}>支持jpg/png，图片大小不超过2MB</Paragraph>
          </Col>
          <Col style={{ width: 140 }} className={styles.previewArea}>
            <h4 className={styles.previewTitle}>预览</h4>
            <div className={styles.previewBox}>
              {previewImage ? (
                <img draggable={false} src={previewImage} alt="头像预览" className={styles.avatarPreview} />
              ) : (
                <UserOutlined className={styles.previewIcon} />
              )}
            </div>
            {previewImage && (
              <Button block onClick={() => setPreviewImage('')}>重新上传</Button>
            )}
          </Col>
        </Row>
      </Modal>

      {/* 编辑姓名弹窗 */}
      <Modal
        title="编辑姓名"
        open={nameModalVisible}
        onCancel={() => {
          setNameModalVisible(false);
          nameForm.resetFields();
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setNameModalVisible(false);
            nameForm.resetFields();
          }}>取消</Button>,
          <Button key="confirm" type="primary" onClick={handleUpdateName}>确定</Button>,
        ]}
      >
        <Form form={nameForm} layout="vertical" className={styles.nameForm}>
          <Form.Item
            label="新姓名"
            name="realName"
            rules={[{ required: true, message: '请输入姓名' }]}
          >
            <Input placeholder="请输入新姓名" maxLength={50} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 绑定邮箱弹窗 */}
      <Modal
        title="绑定邮箱"
        open={emailModalVisible}
        onCancel={() => {
          setEmailModalVisible(false);
          emailForm.resetFields();
        }}
        footer={[
          <Button key="cancel" onClick={() => {
            setEmailModalVisible(false);
            emailForm.resetFields();
          }}>取消</Button>,
          <Button key="confirm" type="primary" onClick={handleUpdateEmail}>确定</Button>,
        ]}
      >
        <Form form={emailForm} layout="vertical" className={styles.emailForm}>
          <Form.Item
            label="新邮箱"
            name="email"
            rules={[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '请输入有效的邮箱' }]}
          >
            <Input placeholder="请输入新邮箱" type="email" prefix={<MailOutlined />} />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  );
};

export default UserSettings;