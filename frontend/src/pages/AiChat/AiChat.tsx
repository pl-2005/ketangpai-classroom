import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Button, Input, Typography, Space, Tag, message,
  List, Popconfirm, Empty, Spin, Divider,
} from 'antd';
import {
  ArrowLeftOutlined, SendOutlined, RobotOutlined, UserOutlined,
  DeleteOutlined, PlusOutlined, ReloadOutlined, FileTextOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { aiChatApi, type ChatMessage, type ChatSession, type ChatReference } from '../../api';
import { useAuth } from '../../contexts/AuthContext';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

/** 解析引用的 JSON 字符串 */
function parseReferences(raw: string | undefined): ChatReference[] {
  if (!raw) return [];
  try {
    return JSON.parse(raw);
  } catch {
    return [];
  }
}

export default function AiChat() {
  const { courseId } = useParams<{ courseId: string }>();
  const numCourseId = Number(courseId);
  const { user } = useAuth();
  const navigate = useNavigate();

  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [rebuilding, setRebuilding] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const isTeacher = user?.role === 'TEACHER';

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 加载会话列表
  useEffect(() => {
    fetchSessions();
  }, [courseId]);

  // 切换会话时加载历史
  useEffect(() => {
    if (activeSessionId) {
      fetchHistory(activeSessionId);
    } else {
      setMessages([]);
    }
  }, [activeSessionId]);

  const fetchSessions = async () => {
    setSessionsLoading(true);
    try {
      const data: any = await aiChatApi.getSessions(numCourseId);
      // 后端可能返回 Result<List<Map>> 或直接 List
      const list = Array.isArray(data) ? data : (data?.data || []);
      // 后端返回的是 List<Map>, 也可能是 List<String>（旧格式兼容）
      const sessions: ChatSession[] = (list || []).map((item: any) => {
        if (typeof item === 'string') {
          return { sessionId: item, title: '对话' };
        }
        return {
          sessionId: item.sessionId,
          title: item.title || '新对话',
          lastMessage: item.lastMessage,
          lastTime: item.lastTime,
        };
      });
      setSessions(sessions);
    } catch {
      // ignore
    } finally {
      setSessionsLoading(false);
    }
  };

  const fetchHistory = async (sessionId: string) => {
    setHistoryLoading(true);
    try {
      const data: any = await aiChatApi.getSessionHistory(numCourseId, sessionId, { size: 100 });
      let list = Array.isArray(data) ? data : (data?.data?.content || data?.content || []);
      // 倒序排列（旧→新）
      if (Array.isArray(list)) {
        list = [...list].reverse();
        setMessages(list);
      }
    } catch {
      message.error('加载对话历史失败');
    } finally {
      setHistoryLoading(false);
    }
  };

  const handleCreateSession = async () => {
    try {
      const data: any = await aiChatApi.createSession(numCourseId);
      const sid = data?.sessionId || data?.data?.sessionId;
      if (sid) {
        setActiveSessionId(sid);
        setMessages([]);
        await fetchSessions();
      }
    } catch {
      message.error('创建会话失败');
    }
  };

  const handleSendMessage = async () => {
    if (!inputValue.trim()) return;
    if (!activeSessionId) {
      // 自动创建会话
      try {
        const data: any = await aiChatApi.createSession(numCourseId);
        const sid = data?.sessionId || data?.data?.sessionId;
        if (sid) {
          setActiveSessionId(sid);
          // 继续发送
          await doSend(sid, inputValue.trim());
          return;
        }
      } catch {
        message.error('创建会话失败');
        return;
      }
    }
    if (!activeSessionId) return;
    await doSend(activeSessionId, inputValue.trim());
  };

  const doSend = async (sessionId: string, content: string) => {
    setInputValue('');
    setLoading(true);

    // 乐观更新：本地添加用户消息
    const tempUserMsg: ChatMessage = {
      id: Date.now(),
      userId: user?.id || 0,
      courseId: numCourseId,
      sessionId,
      role: 'USER',
      content,
      createTime: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, tempUserMsg]);

    try {
      const result: any = await aiChatApi.sendMessage(numCourseId, { sessionId, content });
      const assistantMsg: ChatMessage = result?.data || result;
      setMessages((prev) => [...prev, assistantMsg]);
      // 刷新会话列表以更新最后消息
      fetchSessions();
    } catch {
      message.error('发送失败');
      // 移除乐观更新的用户消息
      setMessages((prev) => prev.filter((m) => m.id !== tempUserMsg.id));
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteSession = async (sessionId: string) => {
    try {
      await aiChatApi.deleteSession(numCourseId, sessionId);
      if (activeSessionId === sessionId) {
        setActiveSessionId(null);
        setMessages([]);
      }
      message.success('会话已删除');
      fetchSessions();
    } catch {
      message.error('删除失败');
    }
  };

  const handleRebuildKnowledge = async () => {
    setRebuilding(true);
    try {
      await aiChatApi.rebuildKnowledge(numCourseId);
      message.success('知识库重建已触发，请稍后开始对话');
    } catch {
      message.error('知识库重建失败');
    } finally {
      setRebuilding(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 160px)', gap: 16 }}>
      {/* 左侧：会话列表 */}
      <Card
        title="对话列表"
        size="small"
        style={{ width: 260, display: 'flex', flexDirection: 'column' }}
        extra={
          <Space size={4}>
            {isTeacher && (
              <Button
                size="small"
                icon={<ReloadOutlined />}
                loading={rebuilding}
                onClick={handleRebuildKnowledge}
                title="重建知识库"
              />
            )}
            <Button size="small" type="primary" icon={<PlusOutlined />} onClick={handleCreateSession}>
              新建
            </Button>
          </Space>
        }
        bodyStyle={{ flex: 1, overflow: 'auto', padding: 8 }}
      >
        {sessionsLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
        ) : sessions.length === 0 ? (
          <Empty description="暂无对话" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <List
            dataSource={sessions}
            renderItem={(s) => (
              <div
                key={s.sessionId}
                onClick={() => setActiveSessionId(s.sessionId)}
                style={{
                  padding: '8px 12px',
                  marginBottom: 4,
                  borderRadius: 6,
                  cursor: 'pointer',
                  background: s.sessionId === activeSessionId ? '#e6f4ff' : 'transparent',
                  border: s.sessionId === activeSessionId ? '1px solid #1677ff' : '1px solid transparent',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Text strong style={{ fontSize: 13, flex: 1 }} ellipsis>
                    {s.title}
                  </Text>
                  <Popconfirm
                    title="确认删除此对话？"
                    onConfirm={(e) => { e?.stopPropagation(); handleDeleteSession(s.sessionId); }}
                    onCancel={(e) => e?.stopPropagation()}
                  >
                    <Button
                      size="small"
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={(e) => e.stopPropagation()}
                    />
                  </Popconfirm>
                </div>
                {s.lastMessage && (
                  <Text type="secondary" style={{ fontSize: 11 }} ellipsis>
                    {s.lastMessage}
                  </Text>
                )}
                {s.lastTime && (
                  <div>
                    <Text type="secondary" style={{ fontSize: 10 }}>
                      {dayjs(s.lastTime).format('MM-DD HH:mm')}
                    </Text>
                  </div>
                )}
              </div>
            )}
          />
        )}
      </Card>

      {/* 右侧：聊天区域 */}
      <Card
        title={
          <Space>
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate(`/courses/${courseId}`)}
              size="small"
            />
            <RobotOutlined />
            <span>AI 答疑</span>
            {!activeSessionId && <Tag>请选择或创建对话</Tag>}
          </Space>
        }
        style={{ flex: 1, display: 'flex', flexDirection: 'column' }}
        bodyStyle={{ flex: 1, display: 'flex', flexDirection: 'column', padding: 0, overflow: 'hidden' }}
        extra={
          isTeacher && (
            <Space size={4}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                知识库管理
              </Text>
              <Button
                size="small"
                icon={<ReloadOutlined />}
                loading={rebuilding}
                onClick={handleRebuildKnowledge}
              >
                重建索引
              </Button>
            </Space>
          )
        }
      >
        {/* 消息列表 */}
        <div style={{ flex: 1, overflow: 'auto', padding: '16px 24px' }}>
          {historyLoading ? (
            <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
          ) : messages.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 60 }}>
              <RobotOutlined style={{ fontSize: 48, color: '#d9d9d9' }} />
              <Text type="secondary" style={{ display: 'block', marginTop: 16, fontSize: 14 }}>
                {activeSessionId
                  ? '开始与 AI 助教对话，询问关于课程内容的任何问题'
                  : '请先创建或选择一个对话'}
              </Text>
              {!activeSessionId && (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  style={{ marginTop: 16 }}
                  onClick={handleCreateSession}
                >
                  新建对话
                </Button>
              )}
            </div>
          ) : (
            messages.map((msg) => (
              <div
                key={msg.id}
                style={{
                  marginBottom: 20,
                  display: 'flex',
                  flexDirection: msg.role === 'USER' ? 'row-reverse' : 'row',
                  alignItems: 'flex-start',
                  gap: 8,
                }}
              >
                {/* 头像 */}
                <div
                  style={{
                    width: 36,
                    height: 36,
                    borderRadius: '50%',
                    background: msg.role === 'USER' ? '#1677ff' : '#52c41a',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  {msg.role === 'USER' ? (
                    <UserOutlined style={{ color: '#fff' }} />
                  ) : (
                    <RobotOutlined style={{ color: '#fff' }} />
                  )}
                </div>

                {/* 消息内容 */}
                <div style={{ maxWidth: '75%' }}>
                  <div
                    style={{
                      padding: '10px 16px',
                      borderRadius: 12,
                      background: msg.role === 'USER' ? '#e6f4ff' : '#f6f8fa',
                      border: msg.role === 'USER' ? '1px solid #91caff' : '1px solid #e8e8e8',
                      whiteSpace: 'pre-wrap',
                      fontSize: 14,
                      lineHeight: 1.7,
                    }}
                  >
                    {msg.content}
                  </div>

                  {/* AI 引用来源 */}
                  {msg.role === 'ASSISTANT' && msg.referencesJson && (() => {
                    const refs = parseReferences(msg.referencesJson);
                    if (refs.length === 0) return null;
                    return (
                      <div style={{ marginTop: 8 }}>
                        <FileTextOutlined style={{ fontSize: 12, marginRight: 4 }} />
                        <Text type="secondary" style={{ fontSize: 12 }}>参考来源：</Text>
                        {refs.map((ref, i) => (
                          <Tag
                            key={i}
                            color="blue"
                            style={{ marginTop: 4, marginRight: 4, fontSize: 11 }}
                          >
                            {ref.sourceName}
                          </Tag>
                        ))}
                      </div>
                    );
                  })()}

                  {/* 时间 */}
                  <Text type="secondary" style={{ fontSize: 11, marginTop: 4, display: 'block' }}>
                    {msg.createTime ? dayjs(msg.createTime).format('HH:mm') : ''}
                  </Text>
                </div>
              </div>
            ))
          )}

          {/* 加载指示器 */}
          {loading && (
            <div style={{ textAlign: 'center', padding: 12 }}>
              <Space>
                <LoadingOutlined />
                <Text type="secondary">AI 思考中...</Text>
              </Space>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        <Divider style={{ margin: 0 }} />

        {/* 输入区域 */}
        <div style={{ padding: '12px 24px' }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
            <TextArea
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入你的问题，按 Enter 发送（Shift+Enter 换行）..."
              rows={2}
              autoSize={{ minRows: 2, maxRows: 6 }}
              style={{ flex: 1 }}
              disabled={loading}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={loading}
              onClick={handleSendMessage}
              disabled={!inputValue.trim()}
              style={{ height: 40 }}
            >
              发送
            </Button>
          </div>
          <Text type="secondary" style={{ fontSize: 11, marginTop: 4, display: 'block' }}>
            AI 助教基于课程资料回答，答案仅供参考。教师可在左侧工具栏重建知识库。
          </Text>
        </div>
      </Card>
    </div>
  );
}
