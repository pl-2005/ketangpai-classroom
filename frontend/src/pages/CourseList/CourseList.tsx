import { useState, useEffect } from 'react';
import { Layout, Breadcrumb, Row, Col, Spin, Empty, message, Avatar, Dropdown, Menu } from 'antd';
import { HomeOutlined, BookOutlined, UserOutlined, LogoutOutlined, SettingOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { courseApi, type Course } from '../../api/courses/courses-api';
import CourseCard from '../../components/CourseCard/CourseCard';
import styles from './CourseList.module.css';

const { Header, Content, Footer } = Layout;

const CourseList = () => {
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    fetchCourses();
  }, []);

  const fetchCourses = async () => {
    setLoading(true);
    try {
      const result = await courseApi.getMyCourses();
      setCourses(result.data.content || []);
    } catch (error) {
      message.error('获取课程列表失败');
      console.error('Failed to fetch courses:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCourseClick = (course: Course) => {
    message.info(`进入课程: ${course.name}`);
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    navigate('/login');
  };

  const handleUserSettings = () => {
    navigate('/user-settings');
  };

  const menuItems = [
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '用户管理',
      onClick: handleUserSettings,
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  const dropdownMenu = (
    <Menu items={menuItems} />
  );

  return (
    <Layout className={styles.layout}>
      <Header className={styles.header}>
        <div className={styles.headerContent}>
          <div className={styles.headerLeft}>
            <div className={styles.logo}>
              <span className={styles.logoText}>AI课堂派</span>
            </div>
            <Breadcrumb className={styles.breadcrumb}>
              <Breadcrumb.Item href="/courses">
                <HomeOutlined />
                <span>首页</span>
              </Breadcrumb.Item>
              <Breadcrumb.Item>
                <BookOutlined />
                <span>我的课堂</span>
              </Breadcrumb.Item>
            </Breadcrumb>
          </div>
          <div className={styles.headerRight}>
            <Dropdown overlay={dropdownMenu} trigger={['click']}>
              <Avatar icon={<UserOutlined />} size={32} className={styles.userAvatar} />
            </Dropdown>
          </div>
        </div>
      </Header>

      <Content className={styles.content}>
        <div className={styles.courseSection}>
          <div className={styles.sectionHeader}>
            <h2 className={styles.sectionTitle}>我的课程</h2>
            <span className={styles.courseCount}>共 {courses.length} 门课程</span>
          </div>

          <Spin spinning={loading}>
            {courses.length > 0 ? (
              <Row gutter={[24, 24]}>
                {courses.map((course) => (
                  <Col xs={24} sm={12} lg={8} key={course.id}>
                    <CourseCard
                      course={course}
                      onClick={() => handleCourseClick(course)}
                    />
                  </Col>
                ))}
              </Row>
            ) : (
              <div className={styles.emptyContainer}>
                <Empty description="暂无课程" />
                <p className={styles.emptyTip}>您还没有加入任何课程</p>
              </div>
            )}
          </Spin>
        </div>
      </Content>

      <Footer className={styles.footer}>
        <p>AI课堂派 - 智慧教育平台</p>
      </Footer>
    </Layout>
  );
};

export default CourseList;
