import { useState, useEffect, useRef } from 'react';
import { Modal, Progress, Typography, Tag, Button, Space, App } from 'antd';
import { LoadingOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { aiGradingApi, type GradingBatchTask } from '../../api';

const { Text } = Typography;

const STATUS_CONFIG: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '等待中' },
  IN_PROGRESS: { color: 'processing', label: '批阅中' },
  COMPLETED: { color: 'success', label: '已完成' },
  PARTIALLY_FAILED: { color: 'warning', label: '部分失败' },
  FAILED: { color: 'error', label: '全部失败' },
};

interface Props {
  assignmentId: number;
  visible: boolean;
  onClose: (completed?: boolean) => void;
}

export default function BatchProgressModal({ assignmentId, visible, onClose }: Props) {
  const { message } = App.useApp();
  const [task, setTask] = useState<GradingBatchTask | null>(null);
  const [polling, setPolling] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (visible) {
      triggerBatch();
    }
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [visible, assignmentId]);

  const triggerBatch = async () => {
    try {
      const result: any = await aiGradingApi.batchAiGrading(assignmentId);
      setTask(result);
      startPolling(result.id);
    } catch (e: any) {
      message.error('启动 AI 批量批阅失败');
      setTask(null);
    }
  };

  const startPolling = (taskId: number) => {
    setPolling(true);
    intervalRef.current = setInterval(async () => {
      try {
        const results: any = await aiGradingApi.getBatchTaskStatus(assignmentId);
        const currentTask = Array.isArray(results) ? results.find((t: GradingBatchTask) => t.id === taskId) : null;
        if (currentTask) {
          setTask(currentTask);
          if (['COMPLETED', 'PARTIALLY_FAILED', 'FAILED'].includes(currentTask.status)) {
            stopPolling();
            // Auto-close after a brief delay on complete
            if (currentTask.status === 'COMPLETED') {
              setTimeout(() => onClose(true), 1500);
            }
          }
        }
      } catch {
        // Continue polling on error
      }
    }, 2000);
  };

  const stopPolling = () => {
    setPolling(false);
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  };

  const handleClose = () => {
    stopPolling();
    onClose(task?.status === 'COMPLETED' || task?.status === 'PARTIALLY_FAILED');
  };

  const statusCfg = task ? STATUS_CONFIG[task.status] || STATUS_CONFIG.PENDING : STATUS_CONFIG.PENDING;
  const percent = task && task.totalCount > 0
    ? Math.round(((task.completedCount + task.failedCount) / task.totalCount) * 100)
    : 0;

  return (
    <Modal
      title="AI 批量批阅"
      open={visible}
      onCancel={handleClose}
      footer={
        <Button onClick={handleClose}>
          {task?.status === 'COMPLETED' ? '完成' : '关闭'}
        </Button>
      }
      closable={!polling || ['COMPLETED', 'PARTIALLY_FAILED', 'FAILED'].includes(task?.status || '')}
      maskClosable={false}
    >
      {!task ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <LoadingOutlined style={{ fontSize: 32 }} />
          <Text style={{ display: 'block', marginTop: 12 }}>正在启动批量批阅...</Text>
        </div>
      ) : (
        <div style={{ textAlign: 'center' }}>
          <Tag color={statusCfg.color} style={{ marginBottom: 16 }}>{statusCfg.label}</Tag>

          <Progress
            type="circle"
            percent={percent}
            status={
              task.status === 'FAILED' ? 'exception' :
              task.status === 'COMPLETED' ? 'success' :
              task.status === 'PARTIALLY_FAILED' ? 'exception' : 'active'
            }
          />

          <div style={{ marginTop: 16 }}>
            <Space direction="vertical">
              <Text>
                已完成：{task.completedCount} / {task.totalCount}
                {task.failedCount > 0 && (
                  <Text type="danger" style={{ marginLeft: 8 }}>
                    {task.failedCount} 份失败
                  </Text>
                )}
              </Text>
              {task.status === 'COMPLETED' && (
                <Text type="success"><CheckCircleOutlined /> 全部批阅完成</Text>
              )}
              {task.status === 'PARTIALLY_FAILED' && (
                <Text type="warning"><CloseCircleOutlined /> 部分批阅失败，可重新触发</Text>
              )}
              {task.status === 'FAILED' && (
                <Text type="danger"><CloseCircleOutlined /> 全部批阅失败，请检查 AI 服务</Text>
              )}
              {task.errorMessage && (
                <Text type="danger" style={{ fontSize: 12 }}>{task.errorMessage}</Text>
              )}
            </Space>
          </div>
        </div>
      )}
    </Modal>
  );
}
