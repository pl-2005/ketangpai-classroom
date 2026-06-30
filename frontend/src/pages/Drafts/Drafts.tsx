import { useState, useEffect, useCallback } from 'react';
import {
  Table, Button, Tag, Select, Space, Typography, Modal, Form, Input,
  InputNumber, DatePicker, Switch, message, Spin, Empty, Popconfirm, Upload,
} from 'antd';
import { PlusOutlined, EditOutlined, SendOutlined, DeleteOutlined, FileTextOutlined } from '@ant-design/icons';
import { filesApi } from '../../api';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { draftsApi, courseApi, type Draft, type DraftType, type Course } from '../../api';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

const { Text } = Typography;

const TYPE_LABELS: Record<DraftType, string> = {
  ASSIGNMENT: '作业',
  MATERIAL: '资料',
  TOPIC: '话题',
};

const TYPE_COLORS: Record<DraftType, string> = {
  ASSIGNMENT: 'blue',
  MATERIAL: 'green',
  TOPIC: 'purple',
};

export default function Drafts() {
  const [drafts, setDrafts] = useState<Draft[]>([]);
  const [loading, setLoading] = useState(false);
  const [typeFilter, setTypeFilter] = useState<DraftType | undefined>(undefined);

  // Create/Edit modal
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [editingId, setEditingId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [draftType, setDraftType] = useState<DraftType>('ASSIGNMENT');
  const [uploading, setUploading] = useState(false);

  // Publish modal
  const [publishOpen, setPublishOpen] = useState(false);
  const [publishDraftId, setPublishDraftId] = useState<number | null>(null);
  const [publishCourseId, setPublishCourseId] = useState<number | undefined>(undefined);
  const [courses, setCourses] = useState<Course[]>([]);

  const fetchDrafts = useCallback(async () => {
    setLoading(true);
    try {
      const data = await draftsApi.getDrafts({ type: typeFilter }) as unknown as Draft[];
      setDrafts(Array.isArray(data) ? data : []);
    } catch {
      message.error('获取草稿列表失败');
    } finally {
      setLoading(false);
    }
  }, [typeFilter]);

  useEffect(() => {
    fetchDrafts();
  }, [fetchDrafts]);

  const fetchCourses = async () => {
    try {
      const data = await courseApi.getMyCourses({ size: 100 }) as unknown as { content: Course[] };
      setCourses(data?.content || []);
    } catch { message.error('获取课程列表失败'); }
  };

  const buildContentJson = (values: Record<string, unknown>, type: DraftType): string => {
    if (type === 'ASSIGNMENT') {
      return JSON.stringify({
        title: values.title,
        content: values.content || '',
        deadline: values.deadline ? dayjs(values.deadline as string).format('YYYY-MM-DD HH:mm:ss') : null,
        maxScore: values.maxScore || 100,
        allowResubmit: values.allowResubmit !== false,
      });
    } else if (type === 'MATERIAL') {
      return JSON.stringify({
        title: values.title,
        type: values.materialType || 'FILE',
        fileUrl: values.fileUrl || '',
        fileSize: values.fileSize || null,
        linkUrl: values.linkUrl || '',
        folderId: values.folderId || null,
      });
    } else {
      return JSON.stringify({
        title: values.title,
        content: values.content || '',
        isAnonymous: values.isAnonymous || false,
      });
    }
  };

  const parseContentJson = (draft: Draft): Record<string, unknown> => {
    try {
      return JSON.parse(draft.contentJson) as Record<string, unknown>;
    } catch {
      return {};
    }
  };

  const handleFileUpload = async (file: File) => {
    setUploading(true);
    try {
      const res = await filesApi.uploadFile(file) as unknown as {
        id: number;
        fileName: string;
        fileUrl: string;
        fileSize: number;
      };
      form.setFieldsValue({
        title: res.fileName,
        fileUrl: res.fileUrl,
        fileSize: res.fileSize,
        fileId: res.id,
      });
      message.success('文件上传成功');
    } catch {
      message.error('文件上传失败');
    } finally {
      setUploading(false);
    }
    return false;
  };

  const handleCreate = () => {
    setEditingId(null);
    form.resetFields();
    setDraftType('ASSIGNMENT');
    setModalOpen(true);
  };

  const handleEdit = (draft: Draft) => {
    setEditingId(draft.id);
    setDraftType(draft.type);
    const data = parseContentJson(draft);
    form.setFieldsValue({
      title: draft.title,
      content: data.content,
      deadline: data.deadline ? dayjs(data.deadline as string) : null,
      maxScore: data.maxScore || 100,
      allowResubmit: data.allowResubmit !== false,
      materialType: data.type || 'FILE',
      fileUrl: data.fileUrl,
      fileSize: data.fileSize,
      linkUrl: data.linkUrl,
      folderId: data.folderId,
      isAnonymous: data.isAnonymous || false,
    });
    setModalOpen(true);
  };

  const handleSubmit = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      const contentJson = buildContentJson(values, draftType);

      if (editingId) {
        await draftsApi.updateDraft(editingId, { title: values.title as string, contentJson });
        message.success('草稿已更新');
      } else {
        await draftsApi.createDraft({ type: draftType, title: values.title as string, contentJson });
        message.success('草稿已创建');
      }
      setModalOpen(false);
      fetchDrafts();
    } catch {
      message.error(editingId ? '更新草稿失败' : '创建草稿失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (draftId: number) => {
    try {
      await draftsApi.deleteDraft(draftId);
      message.success('草稿已删除');
      fetchDrafts();
    } catch { message.error('删除草稿失败'); }
  };

  const handlePublishOpen = async (draftId: number) => {
    setPublishDraftId(draftId);
    setPublishCourseId(undefined);
    await fetchCourses();
    setPublishOpen(true);
  };

  const handlePublish = async () => {
    if (!publishDraftId || !publishCourseId) {
      message.warning('请选择目标课程');
      return;
    }
    setSubmitting(true);
    try {
      await draftsApi.publishDraft(publishDraftId, { courseId: publishCourseId });
      message.success('发布成功');
      setPublishOpen(false);
      fetchDrafts();
    } catch {
      message.error('发布失败');
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      render: (title: string) => <Text strong>{title}</Text>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 90,
      render: (type: DraftType) => <Tag color={TYPE_COLORS[type]}>{TYPE_LABELS[type]}</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 160,
      render: (time: string) => <Text type="secondary">{time ? dayjs(time).fromNow() : '-'}</Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_: unknown, record: Draft) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button
            type="link" size="small" icon={<SendOutlined />}
            onClick={() => handlePublishOpen(record.id)}
          >
            发布
          </Button>
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Space>
          <FileTextOutlined style={{ fontSize: 18 }} />
          <Text strong style={{ fontSize: 18 }}>备课区</Text>
        </Space>
        <Space>
          <Select
            allowClear
            placeholder="全部类型"
            style={{ width: 120 }}
            value={typeFilter}
            onChange={(v) => setTypeFilter(v)}
            options={Object.entries(TYPE_LABELS).map(([value, label]) => ({ value, label }))}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            新建草稿
          </Button>
        </Space>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>
      ) : drafts.length === 0 ? (
        <Empty description="暂无备课草稿">
          <Button type="primary" onClick={handleCreate}>创建第一个草稿</Button>
        </Empty>
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={drafts}
          pagination={{ showSizeChanger: true, showTotal: (total) => `共 ${total} 个草稿` }}
        />
      )}

      {/* Create/Edit Draft Modal */}
      <Modal
        title={editingId ? '编辑草稿' : '新建草稿'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        footer={null}
        destroyOnClose
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item label="草稿类型">
            <Select
              value={draftType}
              onChange={(v) => setDraftType(v)}
              disabled={!!editingId}
              options={Object.entries(TYPE_LABELS).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>

          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="草稿标题" />
          </Form.Item>

          {draftType === 'ASSIGNMENT' && (
            <>
              <Form.Item name="content" label="作业要求">
                <Input.TextArea rows={4} placeholder="作业内容..." />
              </Form.Item>
              <Form.Item name="maxScore" label="满分">
                <InputNumber min={1} max={999} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="deadline" label="截止时间">
                <DatePicker showTime format="YYYY-MM-DD HH:mm:ss" style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="allowResubmit" label="允许重复提交" valuePropName="checked">
                <Switch />
              </Form.Item>
            </>
          )}

          {draftType === 'MATERIAL' && (
            <>
              <Form.Item name="materialType" label="类型" rules={[{ required: true }]} initialValue="FILE">
                <Select options={[
                  { value: 'FILE', label: '文件' },
                  { value: 'LINK', label: '链接' },
                ]} />
              </Form.Item>

              <Form.Item noStyle shouldUpdate={(prev, cur) => prev.materialType !== cur.materialType}>
                {({ getFieldValue }) => {
                  const type = getFieldValue('materialType');
                  if (type === 'LINK') {
                    return (
                      <Form.Item name="linkUrl" label="链接地址" rules={[{ required: true, message: '请输入链接' }]}>
                        <Input placeholder="https://..." />
                      </Form.Item>
                    );
                  }
                  return (
                    <>
                      <Form.Item label="上传文件">
                        <Upload
                          beforeUpload={(file) => { handleFileUpload(file); return false; }}
                          showUploadList={!!form.getFieldValue('fileUrl')}
                          maxCount={1}
                          onRemove={() => {
                            form.setFieldsValue({ fileUrl: undefined, fileSize: undefined, fileId: undefined });
                          }}
                        >
                          <Button icon={<PlusOutlined />} loading={uploading}>选择文件</Button>
                        </Upload>
                      </Form.Item>
                      <Form.Item name="fileUrl" hidden><Input /></Form.Item>
                      <Form.Item name="fileSize" hidden><Input /></Form.Item>
                      <Form.Item name="fileId" hidden><Input /></Form.Item>
                    </>
                  );
                }}
              </Form.Item>
            </>
          )}

          {draftType === 'TOPIC' && (
            <>
              <Form.Item name="content" label="话题内容">
                <Input.TextArea rows={4} placeholder="话题内容..." />
              </Form.Item>
              <Form.Item name="isAnonymous" label="匿名" valuePropName="checked">
                <Switch />
              </Form.Item>
            </>
          )}

          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>
              {editingId ? '保存修改' : '创建草稿'}
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* Publish Modal */}
      <Modal
        title="发布到课程"
        open={publishOpen}
        onCancel={() => setPublishOpen(false)}
        onOk={handlePublish}
        confirmLoading={submitting}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text type="secondary">选择要将此草稿发布到的目标课程：</Text>
          <Select
            style={{ width: '100%' }}
            placeholder="选择课程"
            value={publishCourseId}
            onChange={(v) => setPublishCourseId(v)}
            options={courses.map((c) => ({ value: c.id, label: `${c.name} (${c.courseCode})` }))}
          />
        </Space>
      </Modal>
    </div>
  );
}
