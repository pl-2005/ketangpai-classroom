import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Button, Descriptions, Tag, Input, InputNumber, Table,
  App, Spin, Empty, Typography, Space, Divider, Popconfirm,
} from 'antd';
import {
  ArrowLeftOutlined, CheckOutlined, RollbackOutlined,
  FileTextOutlined, RobotOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { submissionsApi, aiGradingApi, type Submission, type AiGradingResult, type DimensionScore } from '../../api';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

export default function Grading() {
  const { message } = App.useApp();
  const { courseId, assignmentId, submissionId } = useParams<{
    courseId: string; assignmentId: string; submissionId: string;
  }>();
  const numCourseId = Number(courseId);
  const numAssignmentId = Number(assignmentId);
  const numSubmissionId = Number(submissionId);
  const navigate = useNavigate();

  const [submission, setSubmission] = useState<Submission | null>(null);
  const [aiResult, setAiResult] = useState<AiGradingResult | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
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
      // AI grading result from response
      if (data?.aiGradingResult) {
        setAiResult(parseAiResult(data.aiGradingResult));
      }
    } catch {
      message.error('获取提交信息失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSubmission();
  }, [submissionId]);

  /** Parse AI result — normalize detailJson/dimensions */
  const parseAiResult = (raw: any): AiGradingResult => {
    let dimensions: DimensionScore[] | undefined;
    if (raw.detailJson) {
      try { dimensions = JSON.parse(raw.detailJson); } catch {/* keep undefined */}
    }
    return {
      ...raw,
      dimensions: dimensions || raw.dimensions,
    };
  };

  const handleTriggerAi = async () => {
    setAiLoading(true);
    try {
      const result: any = await aiGradingApi.triggerAiGrading(numSubmissionId);
      setAiResult(parseAiResult(result));
      message.success('AI 批阅完成');
    } catch {
      message.error('AI 批阅失败');
    } finally {
      setAiLoading(false);
    }
  };

  const handleUseAiResult = () => {
    if (!aiResult) return;
    if (aiResult.score != null) setScore(aiResult.score);
    if (aiResult.comment) setComment(aiResult.comment);
    message.success('已填入 AI 评分结果，可修改后确认');
  };

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

  const dimensionColumns = [
    { title: '维度', dataIndex: 'dimension', key: 'dimension' },
    { title: '得分', dataIndex: 'score', key: 'score', render: (v: number) => <Text strong>{v}</Text> },
    { title: '满分', dataIndex: 'maxScore', key: 'maxScore' },
    { title: '评语', dataIndex: 'comment', key: 'comment' },
  ];

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

      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'flex-start' }}>
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

        {/* AI Grading Result Card */}
        <Card
          title={
            <Space>
              <RobotOutlined />
              AI 预批阅结果
              {aiResult?.gradedAt && (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {dayjs(aiResult.gradedAt).format('MM-DD HH:mm')}
                </Text>
              )}
            </Space>
          }
          style={{ width: 400 }}
          extra={
            aiResult?.score != null ? (
              <Tag color="blue">AI 评分: {aiResult.score}</Tag>
            ) : null
          }
        >
          {aiResult ? (
            <>
              {/* AI Score */}
              {aiResult.score != null && (
                <div style={{ textAlign: 'center', marginBottom: 16 }}>
                  <Text style={{ fontSize: 36, fontWeight: 700, color: '#1677ff' }}>
                    {aiResult.score}
                  </Text>
                  <Text type="secondary" style={{ fontSize: 16 }}> 分</Text>
                </div>
              )}

              {/* AI Comment */}
              <div style={{ marginBottom: 12 }}>
                <Text strong>AI 评语</Text>
                <div style={{
                  marginTop: 4, padding: 12, background: '#f6f8fa', borderRadius: 8,
                  whiteSpace: 'pre-wrap', fontSize: 13,
                }}>
                  {aiResult.comment}
                </div>
              </div>

              {/* AI Suggestions */}
              {aiResult.suggestions && (
                <div style={{ marginBottom: 12 }}>
                  <Text strong>改进建议</Text>
                  <div style={{
                    marginTop: 4, padding: 12, background: '#fff7e6', borderRadius: 8,
                    whiteSpace: 'pre-wrap', fontSize: 13,
                  }}>
                    {aiResult.suggestions}
                  </div>
                </div>
              )}

              {/* Dimension Detail */}
              {aiResult.dimensions && aiResult.dimensions.length > 0 && (
                <div style={{ marginBottom: 12 }}>
                  <Text strong>各维度评分</Text>
                  <Table
                    dataSource={aiResult.dimensions}
                    columns={dimensionColumns}
                    rowKey="dimension"
                    pagination={false}
                    size="small"
                    style={{ marginTop: 4 }}
                  />
                </div>
              )}

              {/* Action Buttons */}
              <Space direction="vertical" style={{ width: '100%', marginTop: 8 }}>
                <Button block icon={<CheckOutlined />} onClick={handleUseAiResult}>
                  使用 AI 评分
                </Button>
                <Button
                  block
                  icon={<RobotOutlined />}
                  loading={aiLoading}
                  onClick={handleTriggerAi}
                >
                  重新 AI 批阅
                </Button>
              </Space>
            </>
          ) : (
            <div style={{ textAlign: 'center', padding: 24 }}>
              <RobotOutlined style={{ fontSize: 32, color: '#d9d9d9' }} />
              <Text type="secondary" style={{ display: 'block', marginTop: 12, marginBottom: 16 }}>
                尚未进行 AI 批阅
              </Text>
              <Button
                type="primary"
                icon={<RobotOutlined />}
                loading={aiLoading}
                onClick={handleTriggerAi}
              >
                AI 预批阅
              </Button>
            </div>
          )}
        </Card>

        {/* Manual Grading Panel */}
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
