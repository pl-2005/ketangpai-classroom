import { useState, useEffect } from 'react';
import {
  Modal, Switch, Radio, Button, Input, InputNumber, Tag,
  Space, Typography, message, Spin, Popconfirm,
} from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { aiGradingApi, type GradingStyle, type RubricItem, type AiGradingConfig } from '../../api';

const { Text, Title } = Typography;
const { TextArea } = Input;

const STYLE_LABELS: Record<GradingStyle, string> = {
  STRICT: '严格',
  BALANCED: '平衡',
  ENCOURAGING: '鼓励',
  CONCISE: '简洁',
};

interface Props {
  assignmentId: number;
  visible: boolean;
  onClose: () => void;
}

export default function AiGradingConfigPanel({ assignmentId, visible, onClose }: Props) {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [enabled, setEnabled] = useState(false);
  const [gradingStyle, setGradingStyle] = useState<GradingStyle>('BALANCED');
  const [promptTemplate, setPromptTemplate] = useState('');
  const [rubricItems, setRubricItems] = useState<RubricItem[]>([
    { dimension: '内容完整性', weight: 40, maxScore: 40, criteria: '是否全面覆盖了题目要求的所有要点' },
    { dimension: '逻辑与条理', weight: 30, maxScore: 30, criteria: '论证是否逻辑清晰、条理分明' },
    { dimension: '表达与规范', weight: 30, maxScore: 30, criteria: '语言表达是否规范、格式是否整洁' },
  ]);

  const weightSum = rubricItems.reduce((s, i) => s + i.weight, 0);

  useEffect(() => {
    if (visible) fetchConfig();
  }, [visible, assignmentId]);

  const fetchConfig = async () => {
    setLoading(true);
    try {
      const data: any = await aiGradingApi.getAiGradingConfig(assignmentId);
      if (data) {
        setEnabled(data.enabled ?? false);
        setGradingStyle(data.gradingStyle || 'BALANCED');
        setPromptTemplate(data.promptTemplate || '');
        if (data.rubricJson) {
          try {
            const parsed = JSON.parse(data.rubricJson);
            if (Array.isArray(parsed) && parsed.length > 0) {
              setRubricItems(parsed);
            }
          } catch { /* keep defaults */ }
        }
      }
    } catch {
      // Config not set yet — use defaults
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    // Validate
    if (rubricItems.length === 0) {
      message.warning('至少需要一个评分维度');
      return;
    }
    for (let i = 0; i < rubricItems.length; i++) {
      const item = rubricItems[i];
      if (!item.dimension.trim()) { message.warning(`维度[${i + 1}]名称不能为空`); return; }
      if (!item.criteria.trim()) { message.warning(`维度[${i + 1}]标准不能为空`); return; }
      if (item.weight <= 0) { message.warning(`维度[${i + 1}]权重必须为正`); return; }
      if (item.maxScore <= 0) { message.warning(`维度[${i + 1}]满分必须为正`); return; }
    }
    if (weightSum !== 100) {
      message.warning(`权重之和必须为 100，当前为 ${weightSum}`);
      return;
    }

    setSaving(true);
    try {
      await aiGradingApi.updateAiGradingConfig(assignmentId, {
        enabled,
        gradingStyle,
        promptTemplate: promptTemplate || undefined,
        rubricJson: JSON.stringify(rubricItems),
      });
      message.success('AI 批阅配置已保存');
      onClose();
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const addDimension = () => {
    setRubricItems([...rubricItems, { dimension: '', weight: 10, maxScore: 10, criteria: '' }]);
  };

  const removeDimension = (index: number) => {
    if (rubricItems.length <= 1) { message.warning('至少保留一个维度'); return; }
    setRubricItems(rubricItems.filter((_, i) => i !== index));
  };

  const updateDimension = (index: number, field: keyof RubricItem, value: string | number) => {
    const updated = [...rubricItems];
    updated[index] = { ...updated[index], [field]: value };
    setRubricItems(updated);
  };

  if (loading) return <Modal title="AI 批阅设置" open={visible} onCancel={onClose} footer={null}>
    <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
  </Modal>;

  return (
    <Modal
      title="AI 批阅设置"
      open={visible}
      onCancel={onClose}
      onOk={handleSave}
      confirmLoading={saving}
      width={700}
      okText="保存配置"
      cancelText="取消"
    >
      {/* Enable Toggle */}
      <div style={{ marginBottom: 16 }}>
        <Space>
          <Text strong>启用 AI 批阅</Text>
          <Switch checked={enabled} onChange={setEnabled} />
        </Space>
        <Text type="secondary" style={{ display: 'block', marginTop: 4 }}>
          启用后，学生提交作业将自动触发 AI 预评分
        </Text>
      </div>

      {/* Grading Style */}
      <div style={{ marginBottom: 16 }}>
        <Text strong>批阅风格</Text>
        <div style={{ marginTop: 4 }}>
          <Radio.Group value={gradingStyle} onChange={(e) => setGradingStyle(e.target.value)}>
            {Object.entries(STYLE_LABELS).map(([value, label]) => (
              <Radio.Button key={value} value={value}>{label}</Radio.Button>
            ))}
          </Radio.Group>
        </div>
      </div>

      {/* Rubric Editor */}
      <div style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
          <Space>
            <Text strong>评分标准（Rubric）</Text>
            <Tag color={weightSum === 100 ? 'green' : 'red'}>权重和: {weightSum}%</Tag>
          </Space>
          <Button size="small" icon={<PlusOutlined />} onClick={addDimension}>添加维度</Button>
        </div>
        <div style={{ maxHeight: 300, overflow: 'auto', border: '1px solid #f0f0f0', borderRadius: 8, padding: 12 }}>
          {rubricItems.map((item, idx) => (
            <div key={idx} style={{
              marginBottom: 12, padding: 12, background: '#fafafa', borderRadius: 8,
              border: '1px solid #f0f0f0',
            }}>
              <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                <Input
                  placeholder="维度名称"
                  style={{ flex: 2 }}
                  value={item.dimension}
                  onChange={(e) => updateDimension(idx, 'dimension', e.target.value)}
                />
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>权重(%)</Text>
                  <InputNumber
                    min={1} max={100}
                    style={{ width: 80 }}
                    value={item.weight}
                    onChange={(v) => updateDimension(idx, 'weight', v || 0)}
                  />
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>满分</Text>
                  <InputNumber
                    min={1} max={999}
                    style={{ width: 70 }}
                    value={item.maxScore}
                    onChange={(v) => updateDimension(idx, 'maxScore', v || 0)}
                  />
                </div>
                <Popconfirm title="删除此维度？" onConfirm={() => removeDimension(idx)}>
                  <Button size="small" danger icon={<DeleteOutlined />} />
                </Popconfirm>
              </div>
              <TextArea
                rows={2}
                placeholder="评分标准描述..."
                value={item.criteria}
                onChange={(e) => updateDimension(idx, 'criteria', e.target.value)}
              />
            </div>
          ))}
        </div>
      </div>

      {/* Prompt Template */}
      <div style={{ marginBottom: 16 }}>
        <Text strong>Prompt 模板</Text>
        <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>
          支持占位符：{'{rubric}'} {'{submission}'} {'{maxScore}'} {'{gradingStyle}'}
        </Text>
        <TextArea
          rows={6}
          value={promptTemplate}
          onChange={(e) => setPromptTemplate(e.target.value)}
          placeholder="留空使用系统默认模板。自定义模板需包含 {rubric}、{submission}、{maxScore} 占位符。"
        />
      </div>
    </Modal>
  );
}


