import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Button, Typography, Space, Tag, message,
  Spin, Empty, Table, Descriptions, InputNumber,
  Collapse, Alert, Popconfirm, Divider,
} from 'antd';
import {
  ArrowLeftOutlined, ReloadOutlined, CheckCircleOutlined,
  ExclamationCircleOutlined, FileTextOutlined,
  LoadingOutlined, SearchOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  similarityApi, assignmentsApi,
  type SimilarityReport, type SimilarityPair as SimPair, type SimilaritySegment,
} from '../../api';

const { Title, Text, Paragraph } = Typography;

/** 相似度颜色映射 */
function scoreColor(score: number): string {
  if (score >= 0.9) return '#ff4d4f';
  if (score >= 0.8) return '#fa8c16';
  if (score >= 0.7) return '#fadb14';
  return '#52c41a';
}

function scoreTag(score: number) {
  const label = score >= 0.9 ? '极高相似' : score >= 0.8 ? '高度相似' : score >= 0.7 ? '中度相似' : '低度相似';
  return <Tag color={scoreColor(score)}>{label} {(score * 100).toFixed(1)}%</Tag>;
}

export default function SimilarityAnalysis() {
  const { courseId, assignmentId } = useParams<{ courseId: string; assignmentId: string }>();
  const numCourseId = Number(courseId);
  const numAssignmentId = Number(assignmentId);
  const navigate = useNavigate();

  const [assignmentTitle, setAssignmentTitle] = useState('');
  const [reports, setReports] = useState<SimilarityReport[]>([]);
  const [selectedReportId, setSelectedReportId] = useState<number | null>(null);
  const [pairs, setPairs] = useState<SimPair[]>([]);
  const [report, setReport] = useState<SimilarityReport | null>(null);

  const [loading, setLoading] = useState(true);
  const [analyzing, setAnalyzing] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [threshold, setThreshold] = useState(0.80);

  // 加载作业信息和报告列表
  useEffect(() => {
    loadData();
  }, [assignmentId]);

  const loadData = async () => {
    setLoading(true);
    try {
      // 获取作业信息
      const assData: any = await assignmentsApi.getAssignmentDetail(numAssignmentId);
      const ass = assData?.assignment || assData;
      setAssignmentTitle(ass?.title || `作业 #${numAssignmentId}`);

      // 获取报告列表
      const repData: any = await similarityApi.getReports(numAssignmentId);
      const reps: SimilarityReport[] = Array.isArray(repData)
        ? repData : (repData?.data || []);
      setReports(reps);

      // 自动选择最新报告
      if (reps.length > 0) {
        const latest = reps[0];
        setSelectedReportId(latest.id);
        setReport(latest);
        loadReportDetail(latest.id);
      } else {
        setSelectedReportId(null);
        setPairs([]);
        setReport(null);
      }
    } catch {
      message.error('获取相似度分析数据失败');
    } finally {
      setLoading(false);
    }
  };

  const loadReportDetail = async (reportId: number) => {
    setDetailLoading(true);
    try {
      const data: any = await similarityApi.getReportDetail(reportId);
      const detail = data?.data || data;
      setPairs(detail?.pairs || []);
      setReport(detail?.report || report);
    } catch {
      message.error('加载报告详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleAnalyze = async () => {
    setAnalyzing(true);
    try {
      const result: any = await similarityApi.analyze(numAssignmentId, { threshold });
      const newReport: SimilarityReport = result?.data || result;
      message.success(
        `分析完成：共 ${newReport.totalSubmissions} 份提交，` +
        `发现 ${newReport.suspiciousCount} 对疑似相似`
      );
      await loadData();
      if (newReport.id) {
        setSelectedReportId(newReport.id);
        loadReportDetail(newReport.id);
      }
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || '分析失败';
      message.error(msg);
    } finally {
      setAnalyzing(false);
    }
  };

  const handleSelectReport = (reportId: number) => {
    setSelectedReportId(reportId);
    const rep = reports.find((r) => r.id === reportId);
    if (rep) {
      setReport(rep);
      loadReportDetail(reportId);
    }
  };

  // 生成扩展行的相似段落内容
  const expandedRowRender = (pair: SimPair) => {
    const segments: SimilaritySegment[] = pair.highlightedSegments || [];
    if (segments.length === 0) {
      return <Text type="secondary">暂无相似段落详情</Text>;
    }
    return (
      <div style={{ padding: '8px 0' }}>
        <Text strong style={{ fontSize: 13 }}>
          <SearchOutlined /> 相似段落对比（共 {segments.length} 处）
        </Text>
        {segments.map((seg, idx) => (
          <div
            key={idx}
            style={{
              marginTop: 12,
              padding: 12,
              background: '#fffbe6',
              borderRadius: 8,
              border: '1px solid #ffe58f',
            }}
          >
            <Text type="secondary" style={{ fontSize: 11 }}>
              相似度: {(seg.score * 100).toFixed(1)}%
            </Text>
            <div style={{ display: 'flex', gap: 16, marginTop: 8 }}>
              {/* 提交 A 片段 */}
              <div style={{ flex: 1 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {pair.submissionA?.studentName || `学生 #${pair.submissionA?.studentId}`}:
                </Text>
                <div
                  style={{
                    marginTop: 4,
                    padding: 8,
                    background: '#fff',
                    borderRadius: 4,
                    border: '1px solid #e8e8e8',
                    fontSize: 13,
                    whiteSpace: 'pre-wrap',
                    lineHeight: 1.6,
                  }}
                >
                  {seg.textA}
                </div>
              </div>
              {/* 提交 B 片段 */}
              <div style={{ flex: 1 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {pair.submissionB?.studentName || `学生 #${pair.submissionB?.studentId}`}:
                </Text>
                <div
                  style={{
                    marginTop: 4,
                    padding: 8,
                    background: '#fff',
                    borderRadius: 4,
                    border: '1px solid #e8e8e8',
                    fontSize: 13,
                    whiteSpace: 'pre-wrap',
                    lineHeight: 1.6,
                  }}
                >
                  {seg.textB}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    );
  };

  // 相似对表格列定义
  const pairColumns = [
    {
      title: '#',
      key: 'index',
      width: 50,
      render: (_: unknown, __: unknown, idx: number) => idx + 1,
    },
    {
      title: '学生 A',
      key: 'studentA',
      width: 120,
      render: (_: unknown, pair: SimPair) => (
        <Text>{pair.submissionA?.studentName || `ID:${pair.submissionA?.studentId}`}</Text>
      ),
    },
    {
      title: '学生 B',
      key: 'studentB',
      width: 120,
      render: (_: unknown, pair: SimPair) => (
        <Text>{pair.submissionB?.studentName || `ID:${pair.submissionB?.studentId}`}</Text>
      ),
    },
    {
      title: '相似度',
      dataIndex: 'similarityScore',
      key: 'similarityScore',
      width: 180,
      sorter: (a: SimPair, b: SimPair) => a.similarityScore - b.similarityScore,
      render: (score: number) => scoreTag(score),
    },
    {
      title: '提交 A ID',
      key: 'subA',
      width: 70,
      render: (_: unknown, pair: SimPair) => (
        <Text type="secondary" style={{ fontSize: 12 }}>#{pair.submissionA?.id}</Text>
      ),
    },
    {
      title: '提交 B ID',
      key: 'subB',
      width: 70,
      render: (_: unknown, pair: SimPair) => (
        <Text type="secondary" style={{ fontSize: 12 }}>#{pair.submissionB?.id}</Text>
      ),
    },
  ];

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>;
  }

  return (
    <div>
      {/* 返回 */}
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate(`/courses/${courseId}/assignments/${assignmentId}`)}
        style={{ marginBottom: 12 }}
      >
        返回作业详情
      </Button>

      {/* 标题与操作区 */}
      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <FileTextOutlined style={{ fontSize: 18 }} />
            <Title level={4} style={{ margin: 0 }}>
              {assignmentTitle} — 相似度分析
            </Title>
          </Space>
          <Space>
            <Text>阈值:</Text>
            <InputNumber
              min={0.5}
              max={0.99}
              step={0.05}
              value={threshold}
              onChange={(v) => setThreshold(v || 0.80)}
              style={{ width: 80 }}
            />
            <Popconfirm
              title="开始相似度分析？将使用 AI Embedding 对所有提交进行语义比对"
              onConfirm={handleAnalyze}
              okText="开始分析"
              cancelText="取消"
            >
              <Button
                type="primary"
                icon={analyzing ? <LoadingOutlined /> : <SearchOutlined />}
                loading={analyzing}
                danger={reports.length > 0}
              >
                {analyzing ? '分析中...' : '开始相似度分析'}
              </Button>
            </Popconfirm>
            <Button
              icon={<ReloadOutlined />}
              onClick={loadData}
            >
              刷新
            </Button>
          </Space>
        </div>
      </Card>

      {/* 报告列表 */}
      {reports.length === 0 && !analyzing ? (
        <Card>
          <Empty
            description={
              <div>
                <Text type="secondary">暂无相似度分析报告</Text>
                <br />
                <Text type="secondary" style={{ fontSize: 12 }}>
                  点击上方"开始相似度分析"按钮，系统将使用 AI Embedding 模型
                  对作业的全部提交进行语义向量化比对，自动发现高度相似的提交对。
                </Text>
              </div>
            }
          />
        </Card>
      ) : (
        <>
          {/* 历史报告选择器 */}
          {reports.length > 1 && (
            <div style={{ marginBottom: 16 }}>
              <Space wrap>
                <Text strong>历史报告：</Text>
                {reports.map((r) => (
                  <Tag
                    key={r.id}
                    color={r.id === selectedReportId ? 'blue' : 'default'}
                    style={{ cursor: 'pointer', padding: '2px 12px' }}
                    onClick={() => handleSelectReport(r.id)}
                  >
                    {dayjs(r.generatedAt).format('MM-DD HH:mm')}
                    {' — '}
                    {r.suspiciousCount} 对疑似
                  </Tag>
                ))}
              </Space>
            </div>
          )}

          {/* 报告摘要 */}
          {report && (
            <Card size="small" style={{ marginBottom: 16 }}>
              <Descriptions column={4} size="small">
                <Descriptions.Item label="生成时间">
                  {dayjs(report.generatedAt).format('YYYY-MM-DD HH:mm:ss')}
                </Descriptions.Item>
                <Descriptions.Item label="提交人数">
                  {report.totalSubmissions}
                </Descriptions.Item>
                <Descriptions.Item label="相似度阈值">
                  <Tag>{(report.threshold * 100).toFixed(0)}%</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="疑似相似对">
                  {report.suspiciousCount > 0 ? (
                    <Tag color="orange">
                      <ExclamationCircleOutlined /> {report.suspiciousCount} 对
                    </Tag>
                  ) : (
                    <Tag color="green">
                      <CheckCircleOutlined /> 全部正常
                    </Tag>
                  )}
                </Descriptions.Item>
              </Descriptions>
              {report.suspiciousCount > 0 && (
                <Alert
                  type="warning"
                  showIcon
                  icon={<ExclamationCircleOutlined />}
                  message={`发现 ${report.suspiciousCount} 对提交的文本语义高度相似（≥${(report.threshold * 100).toFixed(0)}%），建议教师人工复核。`}
                  style={{ marginTop: 12 }}
                />
              )}
            </Card>
          )}

          {/* 相似对列表 */}
          <Card
            title={
              <Space>
                <SearchOutlined />
                <span>相似提交对</span>
                {report && <Tag>{report.suspiciousCount} 对</Tag>}
              </Space>
            }
          >
            {detailLoading ? (
              <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
            ) : pairs.length === 0 ? (
              <Empty description="未发现高度相似的提交对" />
            ) : (
              <Table
                dataSource={pairs}
                rowKey="id"
                columns={pairColumns}
                expandable={{
                  expandedRowRender,
                  rowExpandable: (pair) =>
                    (pair.highlightedSegments?.length || 0) > 0,
                }}
                pagination={pairs.length > 10 ? { pageSize: 10 } : false}
                size="middle"
              />
            )}
          </Card>
        </>
      )}
    </div>
  );
}
