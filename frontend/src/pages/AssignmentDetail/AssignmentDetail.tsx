import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Button, Typography, Space, Descriptions, Tag, Input, Upload,
  App, Spin, Empty, Divider, Table, Modal, Popconfirm,
} from 'antd';
import {
  ArrowLeftOutlined, UploadOutlined, SendOutlined,
  CloseCircleOutlined, EditOutlined, FileTextOutlined,
  ReloadOutlined, EyeOutlined, RobotOutlined, SettingOutlined, SearchOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { assignmentsApi, submissionsApi, filesApi, aiGradingApi, type Assignment, type Submission, type AiGradingConfig, materialsApi } from '../../api';
import { useAuth } from '../../contexts/AuthContext';
import AiGradingConfigPanel from '../../components/AiGrading/AiGradingConfigPanel';
import BatchProgressModal from '../../components/AiGrading/BatchProgressModal';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

export default function AssignmentDetail() {
  const { message } = App.useApp();
  const { courseId, assignmentId } = useParams<{ courseId: string; assignmentId: string }>();
  const numCourseId = Number(courseId);
  const numAssignmentId = Number(assignmentId);
  const { user } = useAuth();
  const navigate = useNavigate();

  const [assignment, setAssignment] = useState<Assignment | null>(null);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [mySubmission, setMySubmission] = useState<Submission | null>(null);
  const [loading, setLoading] = useState(true);

  // AI grading
  const [aiConfig, setAiConfig] = useState<AiGradingConfig | null>(null);
  const [configPanelOpen, setConfigPanelOpen] = useState(false);
  const [batchModalOpen, setBatchModalOpen] = useState(false);

  // Submit state
  const [content, setContent] = useState('');
  const [fileList, setUploadedFiles] = useState<number[]>([]);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Edit modal
  const [editOpen, setEditOpen] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editContent, setEditContent] = useState('');
  const [editSaving, setEditSaving] = useState(false);

  const [teacherChecked, setTeacherChecked] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [assData] = await Promise.all([
        assignmentsApi.getAssignmentDetail(numAssignmentId) as unknown as any,
      ]);

      const ass = assData?.assignment || assData;
      setAssignment(ass);
    } catch {
      message.error('获取作业信息失败');
    } finally {
      setLoading(false);
    }
  };

  // 获取学生自己的提交（可从外部调用刷新）
  const fetchMySubmission = async () => {
    if (!user || user.role !== 'STUDENT') return;
    try {
      const data = await submissionsApi.getAssignmentSubmissions(numAssignmentId) as unknown as Submission[];
      if (Array.isArray(data) && data.length > 0) {
        setMySubmission(data[0]);
      }
    } catch {
      // 静默处理
    }
  };

  useEffect(() => {
    if (!user) return;

    const fetchSubmissions = async () => {
      try {
        const data = await submissionsApi.getAssignmentSubmissions(numAssignmentId) as unknown as Submission[];
        if (Array.isArray(data)) {
          // 根据 API 返回推断课程角色：
          // - 教师：返回所有学生的提交（含不同 studentId）
          // - 学生：仅返回自己的提交（0-1 条，studentId 均为本人）
          const hasOtherStudents = data.some(s => s.studentId !== user.id);
          // 教师但暂无提交时回落系统角色，避免教师工具栏消失
          const isCourseTeacher = hasOtherStudents
            || (data.length === 0 && user.role === 'TEACHER');
          setTeacherChecked(isCourseTeacher);

          if (isCourseTeacher) {
            setSubmissions(data);
            const mine = data.find((s: Submission) => s.studentId === user.id);
            if (mine) setMySubmission(mine);
          } else if (data.length > 0) {
            setMySubmission(data[0]);
          }
        }
      } catch {
        setTeacherChecked(false);
      }
    };
    fetchSubmissions();
  }, [assignmentId, user]);

  useEffect(() => {
    fetchData();
  }, [assignmentId, teacherChecked]);

  // Fetch AI config in teacher view
  const fetchAiConfig = async () => {
    try {
      const data: any = await aiGradingApi.getAiGradingConfig(numAssignmentId);
      if (data?.assignmentId) setAiConfig(data);
    } catch {
      // AI 配置获取失败，非关键功能，静默处理
    }
  };

  useEffect(() => {
    if (teacherChecked) {
      fetchAiConfig();
    }
  }, [teacherChecked]);

  // ====== Submit ======
  const handleUpload = async (file: File) => {
    setUploading(true);
    try {
      const result: any = await filesApi.uploadFile(file);
      if (result?.id) {
        setUploadedFiles((prev) => [...prev, result.id]);
        message.success(`${file.name} 上传成功`);
      }
    } catch {
      message.error(`${file.name} 上传失败`);
    } finally {
      setUploading(false);
    }
    return false;
  };

  const handleSubmit = async () => {
    if (!content.trim() && fileList.length === 0) {
      message.warning('请填写内容或上传文件');
      return;
    }
    setSubmitting(true);
    try {
      const result: any = await submissionsApi.submitAssignment(numAssignmentId, {
        content: content.trim() || undefined,
        fileIds: fileList.length > 0 ? fileList : undefined,
      });
      message.success('提交成功');
      setContent('');
      setUploadedFiles([]);
      // 立即使用返回的 Submission 更新 mySubmission，切换为已提交视图
      if (result) setMySubmission(result as Submission);
      // 再次获取提交以确保数据完整（含文件、AI 批阅等）
      fetchMySubmission();
      fetchData();
    } catch {
      message.error('提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  // ====== Edit ======
  const handleEditOpen = () => {
    if (!assignment) return;
    setEditTitle(assignment.title);
    setEditContent(assignment.content || '');
    setEditOpen(true);
  };

  const handleEditSave = async () => {
    setEditSaving(true);
    try {
      await assignmentsApi.updateAssignment(numAssignmentId, {
        title: editTitle,
        content: editContent,
      });
      message.success('作业已更新');
      setEditOpen(false);
      fetchData();
    } catch {
      message.error('更新失败');
    } finally {
      setEditSaving(false);
    }
  };

  const statusTag = (status: string) => {
    const map: Record<string, { color: string; text: string }> = {
      DRAFT: { color: 'default', text: '草稿' },
      PUBLISHED: { color: 'blue', text: '已发布' },
      CLOSED: { color: 'red', text: '已关闭' },
    };
    const c = map[status] || { color: 'default', text: status };
    return <Tag color={c.color}>{c.text}</Tag>;
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

  if (!assignment) {
    return <Empty description="作业不存在" />;
  }

  return (
    <div>
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate(`/courses/${courseId}`)}
        style={{ marginBottom: 12 }}
      >
        返回课程
      </Button>

      {/* Assignment Header */}
      <Card style={{ marginBottom: 16 }}>
        <Descriptions
          title={
            <Space>
              {statusTag(assignment.status)}
              <Title level={4} style={{ margin: 0 }}>{assignment.title}</Title>
            </Space>
          }
          extra={
            <Space>
              {teacherChecked && assignment.status === 'PUBLISHED' && (
                <Button icon={<EditOutlined />} onClick={handleEditOpen}>修正</Button>
              )}
            </Space>
          }
          column={2}
          size="small"
        >
          <Descriptions.Item label="满分">{assignment.maxScore} 分</Descriptions.Item>
          <Descriptions.Item label="截止时间">
            {assignment.deadline
              ? dayjs(assignment.deadline).format('YYYY-MM-DD HH:mm')
              : '暂未设置'}
          </Descriptions.Item>
          <Descriptions.Item label="重复提交">
            {assignment.allowResubmit ? '允许' : '不允许'}
          </Descriptions.Item>
        </Descriptions>
        {assignment.content && (
          <div style={{ marginTop: 12, padding: 12, background: '#fafafa', borderRadius: 8 }}>
            <Text type="secondary">作业要求：</Text>
            <Paragraph style={{ marginTop: 8, whiteSpace: 'pre-wrap' }}>{assignment.content}</Paragraph>
          </div>
        )}
      </Card>

      {/* Teacher Toolbar: AI Grading + Similarity */}
      {teacherChecked && (
        <div style={{ marginBottom: 16 }}>
          <Space>
            <Button
              icon={<SettingOutlined />}
              onClick={() => setConfigPanelOpen(true)}
            >
              AI 批阅设置
            </Button>
            {aiConfig?.enabled && (
              <Button
                type="primary"
                icon={<RobotOutlined />}
                onClick={() => setBatchModalOpen(true)}
              >
                AI 批量批阅
              </Button>
            )}
            <Button
              icon={<SearchOutlined />}
              onClick={() => navigate(`/courses/${courseId}/assignments/${assignmentId}/similarity`)}
            >
              相似度分析
            </Button>
            {aiConfig ? (
              <Tag color={aiConfig.enabled ? 'blue' : 'default'}>
                AI: {aiConfig.enabled ? '已启用' : '已禁用'}
              </Tag>
            ) : (
              <Tag color="default">AI: 未配置</Tag>
            )}
          </Space>
        </div>
      )}

      {/* Teacher View: Submissions List */}
      {teacherChecked && (
        <Card title={<span><FileTextOutlined /> 提交列表 ({submissions.length})</span>}>
          {submissions.length === 0 ? (
            <Empty description="暂无学生提交" />
          ) : (
            <Table
              dataSource={submissions}
              rowKey="id"
              pagination={false}
              columns={[
                { title: '学生ID', dataIndex: 'studentId', key: 'studentId', width: 80 },
                { title: '姓名', dataIndex: 'studentName', key: 'studentName' },
                { title: '用户名', dataIndex: 'studentUsername', key: 'studentUsername' },
                {
                  title: '状态', dataIndex: 'status', key: 'status',
                  render: (s: string) => subStatusTag(s),
                },
                {
                  title: '分数', dataIndex: 'score', key: 'score',
                  render: (s: number | undefined) => s != null ? `${s} 分` : '-',
                },
                {
                  title: '版本', dataIndex: 'version', key: 'version',
                  render: (v: number) => `V${v}`,
                },
                {
                  title: '提交时间', dataIndex: 'submittedAt', key: 'submittedAt',
                  render: (t: string) => t ? dayjs(t).format('MM-DD HH:mm') : '-',
                },
                {
                  title: '操作', key: 'action',
                  render: (_: unknown, record: Submission) => (
                    <Button
                      size="small"
                      icon={<EyeOutlined />}
                      onClick={() => navigate(`/courses/${courseId}/assignments/${assignmentId}/grade/${record.id}`)}
                    >
                      批阅
                    </Button>
                  ),
                },
              ]}
            />
          )}
        </Card>
      )}

      {/* Student View: My Submission */}
      {!teacherChecked && assignment.status === 'PUBLISHED' && (
        <>
          {mySubmission ? (
            <Card title="我的提交" style={{ marginBottom: 16 }}>
              <Descriptions column={2} size="small">
                <Descriptions.Item label="状态">{subStatusTag(mySubmission.status)}</Descriptions.Item>
                <Descriptions.Item label="版本">V{mySubmission.version}</Descriptions.Item>
                <Descriptions.Item label="提交时间">
                  {mySubmission.submittedAt ? dayjs(mySubmission.submittedAt).format('YYYY-MM-DD HH:mm') : '-'}
                </Descriptions.Item>
                {mySubmission.status === 'GRADED' && (
                  <>
                    <Descriptions.Item label="分数">
                      <Text strong>{mySubmission.score} / {assignment.maxScore}</Text>
                    </Descriptions.Item>
                    {mySubmission.aiGradingResult && (
                      <>
                        <Descriptions.Item label="AI 预评分">
                          <Tag color="blue">{mySubmission.aiGradingResult.score} 分</Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="AI 评语" span={2}>
                          <div style={{ whiteSpace: 'pre-wrap' }}>{mySubmission.aiGradingResult.comment}</div>
                        </Descriptions.Item>
                        {mySubmission.aiGradingResult.suggestions && (
                          <Descriptions.Item label="AI 建议" span={2}>
                            <div style={{ whiteSpace: 'pre-wrap', background: '#fff7e6', padding: 8, borderRadius: 4 }}>
                              {mySubmission.aiGradingResult.suggestions}
                            </div>
                          </Descriptions.Item>
                        )}
                      </>
                    )}
                    {mySubmission.teacherComment && (
                      <Descriptions.Item label="教师评语" span={2}>
                        {mySubmission.teacherComment}
                      </Descriptions.Item>
                    )}
                  </>
                )}
              </Descriptions>
              {mySubmission.content && (
                <div style={{ marginTop: 12, padding: 12, background: '#fafafa', borderRadius: 8 }}>
                  <Text type="secondary">提交内容：</Text>
                  <Paragraph style={{ marginTop: 8, whiteSpace: 'pre-wrap' }}>{mySubmission.content}</Paragraph>
                </div>
              )}
              {mySubmission.status === 'RETURNED' && (
                <div style={{ marginTop: 12 }}>
                  <Text type="warning">作业已被退回，请修改后重新提交</Text>
                </div>
              )}
            </Card>
          ) : (
            <Card title="提交作业" style={{ marginBottom: 16 }}>
              <div style={{ marginBottom: 16 }}>
                <TextArea
                  rows={6}
                  placeholder="请输入作业内容..."
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                />
              </div>
              <Space direction="vertical" style={{ width: '100%' }}>
                <Upload
                  beforeUpload={(file) => { handleUpload(file); return false; }}
                  showUploadList={{ showRemoveIcon: true }}
                  onRemove={() => setUploadedFiles([])}
                  fileList={[]}
                >
                  <Button icon={<UploadOutlined />} loading={uploading}>
                    上传附件
                  </Button>
                </Upload>
                {fileList.length > 0 && (
                  <Text type="secondary">已上传 {fileList.length} 个文件</Text>
                )}
              </Space>
              <Divider />
              <Space>
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  loading={submitting}
                  onClick={handleSubmit}
                  disabled={!content.trim() && fileList.length === 0}
                >
                  提交作业
                </Button>
                {assignment.allowResubmit && mySubmission && (
                  <Button
                    icon={<ReloadOutlined />}
                    onClick={() => {
                      setContent((mySubmission as Submission).content || '');
                    }}
                  >
                    加载上次提交
                  </Button>
                )}
              </Space>
            </Card>
          )}
        </>
      )}

      {/* Student: Resubmit */}
      {!teacherChecked && mySubmission && assignment.status === 'PUBLISHED' && assignment.allowResubmit && (
        <Card title="重新提交" style={{ marginBottom: 16 }}>
          <TextArea
            rows={6}
            placeholder="请输入新的作业内容..."
            value={content}
            onChange={(e) => setContent(e.target.value)}
            style={{ marginBottom: 12 }}
          />
          <Upload
            beforeUpload={(file) => { handleUpload(file); return false; }}
            showUploadList={{ showRemoveIcon: true }}
            onRemove={() => setUploadedFiles([])}
            fileList={[]}
          >
            <Button icon={<UploadOutlined />} loading={uploading}>上传新附件</Button>
          </Upload>
          {fileList.length > 0 && (
            <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>已上传 {fileList.length} 个文件</Text>
          )}
          <Divider />
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={submitting}
            onClick={handleSubmit}
          >
            重新提交
          </Button>
        </Card>
      )}

      {/* AI Grading Config Panel */}
      <AiGradingConfigPanel
        assignmentId={numAssignmentId}
        visible={configPanelOpen}
        onClose={() => { setConfigPanelOpen(false); fetchAiConfig(); }}
      />

      {/* Batch Progress Modal */}
      <BatchProgressModal
        assignmentId={numAssignmentId}
        visible={batchModalOpen}
        onClose={(completed) => {
          setBatchModalOpen(false);
          if (completed) fetchData();
        }}
      />

      {/* Edit Assignment Modal */}
      <Modal
        title="修正作业"
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={handleEditSave}
        confirmLoading={editSaving}
        width={600}
      >
        <div style={{ marginBottom: 12 }}>
          <Text strong>标题</Text>
          <Input value={editTitle} onChange={(e) => setEditTitle(e.target.value)} style={{ marginTop: 4 }} />
        </div>
        <div>
          <Text strong>作业要求</Text>
          <TextArea
            rows={6}
            value={editContent}
            onChange={(e) => setEditContent(e.target.value)}
            style={{ marginTop: 4 }}
          />
        </div>
      </Modal>
    </div>
  );
}
