import { Card, Tag, Avatar } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import type { Course } from '../../api/courses/courses-api';
import styles from './CourseCard.module.css';

interface CourseCardProps {
  course: Course;
  onClick?: () => void;
}

const CourseCard = ({ course, onClick }: CourseCardProps) => {
  const coverColors = [
    'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
    'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)',
  ];

  const colorIndex = course.id % coverColors.length;
  const coverStyle = {
    background: coverColors[colorIndex],
  };

  return (
    <Card
      className={styles.courseCard}
      hoverable
      onClick={onClick}
      cover={
        <div className={styles.cardCover} style={coverStyle}>
          <div className={styles.coverContent}>
            <h3 className={styles.courseName}>{course.name}</h3>
            <p className={styles.courseCode}>{course.courseCode}</p>
            <div className={styles.addCode}>
              <span className={styles.addCodeIcon}>⊕</span>
              <span className={styles.addCodeText}>加课码:{course.courseCode.substring(0, 8)}</span>
            </div>
          </div>
        </div>
      }
    >
      <div className={styles.cardContent}>
        <div className={styles.memberInfo}>
          <Avatar icon={<UserOutlined />} size={24} />
          <span className={styles.memberCount}>{course.memberCount} 名成员</span>
        </div>
        <div className={styles.courseActions}>
          <Tag color={course.status === 'ACTIVE' ? 'green' : 'gray'}>
            {course.status === 'ACTIVE' ? '进行中' : '已归档'}
          </Tag>
        </div>
      </div>
    </Card>
  );
};

export default CourseCard;
