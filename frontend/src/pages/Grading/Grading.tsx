import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Button, Descriptions, Tag, Input, InputNumber,
  message, Spin, Empty, Typography, Space, Divider, Popconfirm,
} from 'antd';
import {
  ArrowLeftOutlined, CheckOutlined, RollbackOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { submissionsApi, type Submission } from '../../api';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

export default function Grading() {
  const { courseId, assignmentId, submissionId } = useParams<{
    courseId: string; assignmentId: string; submissionId: string;
  }>();
  const numCourseId = Number(courseId);
  const numAssignmentId = Number(assignmentId);
  const numSubmissionId = Number(submissionId);
  const navigate = useNavigate();

  const [submission, setSubmission] = useState<Submission | null>(null);
  const [loading, setLoading] = useState(true);
  const [score, setScore] = useState<number | null>(null);
  const [comment, setComment] = useState('');
  const [returnReason, setReturnReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const fetchSubmission = async () => {
    setLoading(true);
    try {
      const data: any = await submissionsApi.getSubmissionDetail(numSubmissionId);
      const sub = data?.submission || data;
      setSubmission(sub);
      setScore(sub.score || null);
      setComment(sub.teacherComment || '');
    } catch {
      message.error('获取提交信息失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSubmission();
  }, [submissionId]);

  const handleGrade = async () => {
    if (score == null) {
      message.warning('请输入分数');
      return;
    }
    setSubmitting(true);
    try {
      await submissionsApi.gradeSubmission(numSubmissionId, {
        score,
        teacherComment: comment || undefined,
      });
      message.success('评分成功');
      navigate(`/courses/${courseId}/assignments/${assignmentId}`);
    } catch {
      message.error('评分失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleReturn = async () => {
    setSubmitting(true);
    try {
      await submissionsApi.returnSubmission(numSubmissionId, {
        reason: returnReason || '作业需修改',
      });
      message.success('作业已退回');
      navigate(`/courses/${courseId}/assignments/${assignmentId}`);
    } catch {
      message.error('退回失败');
    } finally {
      setSubmitting(false);
    }
  };

  const subStatusTag = (status: string) => {
    const map: Record<string, { color: string; text: string }> = {
      SUBMITTED: { color: 'blue', text: '已提交' },
      GRADED: { color: 'green', text: '已批阅' },
      RETURNED: { color: 'orange', text: '已退回' },
      DRAFT: { color: 'default', text: '草稿' },
    };
    const c = map[status] || { color: 'default', text: status };
    return <Tag color={c.color}>{c.text}</Tag>;
  };

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>;
  }

  if (!submission) {
    return <Empty description="提交不存在" />;
  }

  return (
    <div>
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate(`/courses/${courseId}/assignments/${assignmentId}`)}
        style={{ marginBottom: 12 }}
      >
        返回作业
      </Button>

      <Title level={4}>批阅提交</Title>

      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        {/* Submission Detail */}
        <Card
          title={
            <Space>
              <FileTextOutlined />
              提交详情
              {subStatusTag(submission.status)}
            </Space>
          }
          style={{ flex: 1, minWidth: 300 }}
        >
          <Descriptions column={1} size="small">
            <Descriptions.Item label="学生ID">{submission.studentId}</Descriptions.Item>
            <Descriptions.Item label="姓名">{submission.studentName || '-'}</Descriptions.Item>
            <Descriptions.Item label="用户名">{submission.studentUsername || '-'}</Descriptions.Item>
            <Descriptions.Item label="版本">V{submission.version}</Descriptions.Item>
            <Descriptions.Item label="提交时间">
              {submission.submittedAt
                ? dayjs(submission.submittedAt).format('YYYY-MM-DD HH:mm:ss')
                : '-'}
            </Descriptions.Item>
            {submission.gradedAt && (
              <Descriptions.Item label="批阅时间">
                {dayjs(submission.gradedAt).format('YYYY-MM-DD HH:mm:ss')}
              </Descriptions.Item>
            )}
          </Descriptions>

          {submission.content && (
            <div style={{ marginTop: 12, padding: 12, background: '#fafafa', borderRadius: 8 }}>
              <Text type="secondary">提交内容：</Text>
              <Paragraph style={{ marginTop: 8, whiteSpace: 'pre-wrap' }}>{submission.content}</Paragraph>
            </div>
          )}

          {submission.files && submission.files.length > 0 && (
            <div style={{ marginTop: 12 }}>
              <Text type="secondary">附件：</Text>
              {submission.files.map((f) => (
                <div key={f.id}>
                  <Text>{f.fileName} ({f.fileSize} bytes)</Text>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Grading Panel */}
        <Card title="评分" style={{ width: 360 }}>
          {submission.status === 'GRADED' ? (
            <div>
              <Descriptions column={1} size="small">
                <Descriptions.Item label="已评分数">
                  <Text strong style={{ fontSize: 18 }}>{submission.score} 分</Text>
                </Descriptions.Item>
                <Descriptions.Item label="评语">
                  {submission.teacherComment || '无'}
                </Descriptions.Item>
              </Descriptions>
              <Divider />
              <Text type="secondary">修改评分：</Text>
              <div style={{ marginTop: 8 }}>
                <Text strong>分数</Text>
                <InputNumber
                  min={0}
                  style={{ width: '100%', marginTop: 4 }}
                  value={score}
                  onChange={(v) => setScore(v)}
                />
              </div>
              <div style={{ marginTop: 12 }}>
                <Text strong>评语</Text>
                <TextArea
                  rows={3}
                  style={{ marginTop: 4 }}
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  placeholder="可选：修改评语"
                />
              </div>
              <Button
                type="primary"
                icon={<CheckOutlined />}
                block
                style={{ marginTop: 16 }}
                loading={submitting}
                onClick={handleGrade}
              >
                更新评分
              </Button>
            </div>
          ) : (
            <div>
              <div style={{ marginBottom: 16 }}>
                <Text strong>分数</Text>
                <InputNumber
                  min={0}
                  max={100}
                  style={{ width: '100%', marginTop: 4 }}
                  placeholder="请输入分数"
                  value={score}
                  onChange={(v) => setScore(v)}
                />
              </div>
              <div style={{ marginBottom: 16 }}>
                <Text strong>评语</Text>
                <TextArea
                  rows={4}
                  style={{ marginTop: 4 }}
                  placeholder="可选：输入教师评语"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                />
              </div>
              <Space direction="vertical" style={{ width: '100%' }}>
                <Button
                  type="primary"
                  icon={<CheckOutlined />}
                  block
                  loading={submitting}
                  onClick={handleGrade}
                >
                  确认评分
                </Button>
                {submission.status === 'SUBMITTED' && (
                  <div>
                    <Divider />
                    <Text type="secondary">或退回作业（学生修改后可重新提交）</Text>
                    <TextArea
                      rows={2}
                      style={{ marginTop: 8 }}
                      placeholder="退回原因"
                      value={returnReason}
                      onChange={(e) => setReturnReason(e.target.value)}
                    />
                    <Popconfirm
                      title="确认退回？学生将看到退回原因"
                      onConfirm={handleReturn}
                    >
                      <Button
                        danger
                        icon={<RollbackOutlined />}
                        block
                        style={{ marginTop: 8 }}
                        loading={submitting}
                      >
                        退回作业
                      </Button>
                    </Popconfirm>
                  </div>
                )}
              </Space>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
