import { useState, useEffect, useCallback } from 'react';
import { Tree, Button, Modal, Form, Input, Upload, Select, Space, Typography, App, Spin, Empty, Popconfirm } from 'antd';
import {
  FolderOutlined, FileOutlined, LinkOutlined, PlusOutlined,
  DeleteOutlined, EditOutlined, DownloadOutlined, EyeOutlined, FolderAddOutlined,
} from '@ant-design/icons';
import type { DataNode } from 'antd/es/tree';
import { materialsApi, filesApi, type Material, type MaterialFolder } from '../../api';

const { Text } = Typography;

interface TreeNodeData {
  folder: MaterialFolder | null;
  materials: Material[];
  children: TreeNodeData[];
}

interface Props {
  courseId: number;
  isTeacher: boolean;
}

export default function MaterialsTab({ courseId, isTeacher }: Props) {
  const { message } = App.useApp();
  const [treeData, setTreeData] = useState<TreeNodeData[]>([]);
  const [loading, setLoading] = useState(false);
  const [antTreeData, setAntTreeData] = useState<DataNode[]>([]);

  // Modals
  const [folderOpen, setFolderOpen] = useState(false);
  const [folderForm] = Form.useForm();
  const [materialOpen, setMaterialOpen] = useState(false);
  const [materialForm] = Form.useForm();
  const [renameOpen, setRenameOpen] = useState(false);
  const [renameForm] = Form.useForm();
  const [renameTarget, setRenameTarget] = useState<{ id: number; isFolder: boolean } | null>(null);
  const [moveOpen, setMoveOpen] = useState(false);
  const [moveMaterialId, setMoveMaterialId] = useState<number | null>(null);
  const [moveTargetId, setMoveTargetId] = useState<number | undefined>(undefined);

  // Upload state
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const fetchTree = useCallback(async () => {
    setLoading(true);
    try {
      const data = await materialsApi.getMaterialTree(courseId) as unknown as TreeNodeData[];
      setTreeData(data || []);
    } catch {
      message.error('获取课程资料失败');
    } finally {
      setLoading(false);
    }
  }, [courseId]);

  useEffect(() => {
    fetchTree();
  }, [fetchTree]);

  // Build Ant Design Tree data
  useEffect(() => {
    const buildTreeNodes = (nodes: TreeNodeData[]): DataNode[] => {
      return nodes.map((node, idx) => {
        const children: DataNode[] = [];

        if (node.folder) {
          // Folder node
          children.push(...buildTreeNodes(node.children || []));
          node.materials?.forEach((m) => {
            children.push(materialToNode(m));
          });

          return {
            key: `folder-${node.folder.id}`,
            title: renderFolderTitle(node.folder),
            icon: <FolderOutlined />,
            children: children.length > 0 ? children : undefined,
            isLeaf: children.length === 0,
          };
        } else {
          // Root materials
          node.materials?.forEach((m) => {
            children.push(materialToNode(m));
          });
          return {
            key: `root-${idx}`,
            title: <Text type="secondary">根目录</Text>,
            icon: <FolderOutlined />,
            children: children.length > 0 ? children : undefined,
          };
        }
      });
    };

    setAntTreeData(buildTreeNodes(treeData));
  }, [treeData]);

  const materialToNode = (m: Material): DataNode => ({
    key: `material-${m.id}`,
    title: renderMaterialTitle(m),
    icon: m.type === 'LINK' ? <LinkOutlined /> : <FileOutlined />,
    isLeaf: true,
  });

  const renderFolderTitle = (folder: MaterialFolder) => (
    <Space>
      <Text strong>{folder.name}</Text>
      {isTeacher && (
        <span style={{ opacity: 0.6 }}>
          <Button
            type="text" size="small" icon={<EditOutlined />}
            onClick={(e) => { e.stopPropagation(); handleRenameOpen(folder.id, true, folder.name); }}
          />
          <Popconfirm
            title="删除文件夹及其所有子内容？"
            onConfirm={(e) => { e?.stopPropagation(); handleDeleteFolder(folder.id); }}
            onCancel={(e) => e?.stopPropagation()}
          >
            <Button
              type="text" size="small" danger icon={<DeleteOutlined />}
              onClick={(e) => e.stopPropagation()}
            />
          </Popconfirm>
        </span>
      )}
    </Space>
  );

  const renderMaterialTitle = (m: Material) => (
    <Space>
      <Text>{m.title}</Text>
      {m.type === 'FILE' && m.fileSize && (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {m.fileSize > 1024 * 1024
            ? `${(m.fileSize / (1024 * 1024)).toFixed(1)} MB`
            : `${Math.round(m.fileSize / 1024)} KB`}
        </Text>
      )}
      {isTeacher && (
        <span style={{ opacity: 0.6 }}>
          <Button
            type="text" size="small" icon={<EditOutlined />}
            onClick={(e) => { e.stopPropagation(); handleRenameOpen(m.id, false, m.title); }}
          />
          <Button
            type="text" size="small" icon={<FolderAddOutlined />}
            onClick={(e) => { e.stopPropagation(); handleMoveOpen(m.id); }}
          />
          <Popconfirm
            title="确认删除？"
            onConfirm={(e) => { e?.stopPropagation(); handleDeleteMaterial(m.id); }}
            onCancel={(e) => e?.stopPropagation()}
          >
            <Button
              type="text" size="small" danger icon={<DeleteOutlined />}
              onClick={(e) => e.stopPropagation()}
            />
          </Popconfirm>
        </span>
      )}
      {m.type === 'FILE' && (
        <>
          <Button
            type="text" size="small" icon={<DownloadOutlined />}
            onClick={async (e) => {
              e.stopPropagation();
              try {
                const res = await materialsApi.getMaterialDownloadUrl(m.id) as unknown as { url: string };
                window.open(res.url, '_blank');
              } catch {
                message.error('获取下载链接失败');
              }
            }}
          />
        </>
      )}
      {m.type === 'LINK' && m.linkUrl && (
        <Button
          type="text" size="small" icon={<LinkOutlined />}
          onClick={(e) => { e.stopPropagation(); window.open(m.linkUrl, '_blank'); }}
        />
      )}
    </Space>
  );

  // Folder CRUD
  const handleCreateFolder = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      await materialsApi.createFolder({
        courseId,
        name: values.name as string,
        parentId: values.parentId as number | undefined,
      });
      message.success('文件夹创建成功');
      setFolderOpen(false);
      folderForm.resetFields();
      fetchTree();
    } catch {
      message.error('创建文件夹失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteFolder = async (folderId: number) => {
    try {
      await materialsApi.deleteFolder(folderId);
      message.success('文件夹已删除');
      fetchTree();
    } catch {
      message.error('删除文件夹失败');
    }
  };

  // Material CRUD
  const handleCreateMaterial = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      const materialType = values.type as string;
      const payload: Record<string, unknown> = {
        courseId,
        title: values.title as string,
        type: materialType,
        folderId: values.folderId as number | undefined,
      };

      if (materialType === 'LINK') {
        payload.linkUrl = values.linkUrl as string;
      } else {
        // FILE type
        if (values.fileUrl) payload.fileUrl = values.fileUrl as string;
        if (values.fileSize) payload.fileSize = values.fileSize as number;
        if (values.fileId) payload.fileId = values.fileId as number;
      }

      await materialsApi.createMaterial(payload as unknown as Parameters<typeof materialsApi.createMaterial>[0]);
      message.success('资料创建成功');
      setMaterialOpen(false);
      materialForm.resetFields();
      fetchTree();
    } catch {
      message.error('创建资料失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteMaterial = async (materialId: number) => {
    try {
      await materialsApi.deleteMaterial(materialId);
      message.success('资料已删除');
      fetchTree();
    } catch {
      message.error('删除资料失败');
    }
  };

  // File upload
  const handleFileUpload = async (file: File) => {
    setUploading(true);
    try {
      const res = await filesApi.uploadFile(file) as unknown as {
        id: number;
        fileName: string;
        fileUrl: string;
        fileSize: number;
      };
      materialForm.setFieldsValue({
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
    return false; // Prevent default Upload behavior
  };

  // Rename
  const handleRenameOpen = (id: number, isFolder: boolean, currentName: string) => {
    setRenameTarget({ id, isFolder });
    renameForm.setFieldsValue({ name: currentName });
    setRenameOpen(true);
  };

  const handleRename = async (values: Record<string, unknown>) => {
    if (!renameTarget) return;
    setSubmitting(true);
    try {
      if (renameTarget.isFolder) {
        // Folder rename - use same API pattern
        await materialsApi.updateMaterial(renameTarget.id, { title: values.name as string });
      } else {
        await materialsApi.updateMaterial(renameTarget.id, { title: values.name as string });
      }
      message.success('重命名成功');
      setRenameOpen(false);
      fetchTree();
    } catch {
      message.error('重命名失败');
    } finally {
      setSubmitting(false);
    }
  };

  // Move
  const handleMoveOpen = (materialId: number) => {
    setMoveMaterialId(materialId);
    setMoveTargetId(undefined);
    setMoveOpen(true);
  };

  const handleMove = async () => {
    if (!moveMaterialId) return;
    setSubmitting(true);
    try {
      await materialsApi.moveMaterial(moveMaterialId, {
        targetFolderId: moveTargetId,
      });
      message.success('移动成功');
      setMoveOpen(false);
      fetchTree();
    } catch {
      message.error('移动失败');
    } finally {
      setSubmitting(false);
    }
  };

  // Build folder options for selectors
  const buildFolderOptions = (nodes: TreeNodeData[]): { value: number; label: string }[] => {
    const options: { value: number; label: string }[] = [];
    const walk = (n: TreeNodeData, prefix: string) => {
      if (n.folder) {
        options.push({ value: n.folder.id, label: `${prefix}${n.folder.name}` });
        n.children?.forEach((c) => walk(c, `${prefix}${n.folder!.name}/`));
      }
    };
    nodes.forEach((n) => {
      if (n.folder) walk(n, '');
      n.materials?.length; // only add folders
    });
    return options;
  };

  const folderOptions = buildFolderOptions(treeData);
  const moveFolderOptions = [
    { value: undefined as unknown as number, label: '根目录（无文件夹）' },
    ...folderOptions,
  ];

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>;
  }

  return (
    <div>
      {isTeacher && (
        <Space style={{ marginBottom: 16 }}>
          <Button
            icon={<FolderAddOutlined />}
            onClick={() => { folderForm.resetFields(); setFolderOpen(true); }}
          >
            新建文件夹
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => { materialForm.resetFields(); setMaterialOpen(true); }}
          >
            创建资料
          </Button>
        </Space>
      )}

      {antTreeData.length === 0 ? (
        <Empty description="暂无课程资料" />
      ) : (
        <Tree
          showIcon
          defaultExpandAll
          treeData={antTreeData}
          style={{ background: 'transparent' }}
          blockNode
        />
      )}

      {/* Create Folder Modal */}
      <Modal
        title="新建文件夹"
        open={folderOpen}
        onCancel={() => setFolderOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Form form={folderForm} layout="vertical" onFinish={handleCreateFolder}>
          <Form.Item name="name" label="文件夹名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="例如：第一章" />
          </Form.Item>
          <Form.Item name="parentId" label="父文件夹">
            <Select allowClear placeholder="无（根目录）" options={folderOptions} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>创建</Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* Create Material Modal */}
      <Modal
        title="创建资料"
        open={materialOpen}
        onCancel={() => setMaterialOpen(false)}
        footer={null}
        destroyOnClose
        width={560}
      >
        <Form form={materialForm} layout="vertical" onFinish={handleCreateMaterial}
          initialValues={{ type: 'FILE' }}>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={[
              { value: 'FILE', label: '文件' },
              { value: 'LINK', label: '链接' },
            ]} />
          </Form.Item>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="资料标题" />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.type !== cur.type}>
            {({ getFieldValue }) => {
              const type = getFieldValue('type');
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
                      showUploadList={!!materialForm.getFieldValue('fileUrl')}
                      maxCount={1}
                      onRemove={() => {
                        materialForm.setFieldsValue({ fileUrl: undefined, fileSize: undefined, fileId: undefined });
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
          <Form.Item name="folderId" label="所属文件夹">
            <Select allowClear placeholder="无（根目录）" options={folderOptions} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>创建</Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* Rename Modal */}
      <Modal
        title="重命名"
        open={renameOpen}
        onCancel={() => setRenameOpen(false)}
        footer={null}
        destroyOnClose
      >
        <Form form={renameForm} layout="vertical" onFinish={handleRename}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="新名称" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>保存</Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* Move Modal */}
      <Modal
        title="移动到"
        open={moveOpen}
        onCancel={() => setMoveOpen(false)}
        onOk={handleMove}
        confirmLoading={submitting}
      >
        <Select
          style={{ width: '100%' }}
          value={moveTargetId}
          onChange={(v) => setMoveTargetId(v)}
          options={moveFolderOptions}
        />
      </Modal>
    </div>
  );
}
