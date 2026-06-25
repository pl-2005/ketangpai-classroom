package com.ketangpai.service;

import com.ketangpai.model.entity.ChatMessage;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.ChatRole;
import com.ketangpai.repository.ChatMessageRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.KnowledgeChunkRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 答疑机器人服务（RAG 架构）
 */
@Service
public class ChatService extends BaseService {

    private final ChatMessageRepository chatMessageRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;

    public ChatService(CourseMemberRepository courseMemberRepository,
                       ChatMessageRepository chatMessageRepository,
                       KnowledgeChunkRepository knowledgeChunkRepository) {
        super(courseMemberRepository);
        this.chatMessageRepository = chatMessageRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
    }

    @Transactional
    public String createSession(Long courseId, Long userId) {
        getMemberOrThrow(courseId, userId);
        return UUID.randomUUID().toString();
    }

    public List<String> listSessions(Long courseId, Long userId) {
        getMemberOrThrow(courseId, userId);
        return chatMessageRepository.findDistinctSessionsByUserIdAndCourseId(userId, courseId);
    }

    @Transactional
    public ChatMessage chat(Long courseId, Long userId, String sessionId, String question) {
        getMemberOrThrow(courseId, userId);

        // 1. 保存用户提问
        ChatMessage userMsg = ChatMessage.builder()
                .userId(userId)
                .courseId(courseId)
                .sessionId(sessionId)
                .role(ChatRole.USER)
                .content(question)
                .build();
        chatMessageRepository.save(userMsg);

        // 2. RAG 检索：向量搜索相关课程内容
        // TODO: 实际 RAG 实现
        //   a. 调用 EmbeddingClient 向量化问题
        //   b. Qdrant 向量相似度搜索
        //   c. 根据 qdrantPointId 从 knowledge_chunk 获取原始文本
        //   d. 拼接上下文 + 历史对话 → LLM 生成回答
        //   e. 构建 referencesJson

        String answer = generateAnswer(courseId, question);

        // 3. 保存 AI 回答
        ChatMessage assistantMsg = ChatMessage.builder()
                .userId(userId)
                .courseId(courseId)
                .sessionId(sessionId)
                .role(ChatRole.ASSISTANT)
                .content(answer)
                .referencesJson(null) // TODO: 填充引用来源
                .build();

        return chatMessageRepository.save(assistantMsg);
    }

    /** 获取指定会话的对话历史（分页） */
    public Page<ChatMessage> getHistory(Long courseId, String sessionId, Long userId, Pageable pageable) {
        getMemberOrThrow(courseId, userId);
        // 校验用户是该会话的参与者
        if (!isSessionParticipant(sessionId, userId)) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return chatMessageRepository.findBySessionIdOrderByCreateTimeDesc(sessionId, pageable);
    }

    @Transactional
    public void deleteSession(String sessionId, Long userId) {
        // 校验 userId 是该会话的参与者（有至少一条消息）
        if (!isSessionParticipant(sessionId, userId)) {
            throw new BusinessException(403, "无权删除该会话");
        }
        chatMessageRepository.deleteBySessionId(sessionId);
    }

    /** 检查用户是否参与了指定会话 */
    private boolean isSessionParticipant(String sessionId, Long userId) {
        Page<ChatMessage> page = chatMessageRepository
                .findBySessionIdOrderByCreateTimeDesc(sessionId, Pageable.ofSize(1));
        return page.getContent().stream().anyMatch(msg -> msg.getUserId().equals(userId));
    }

    /** 占位：后续接入 Spring AI 实现真正的 RAG 回答 */
    private String generateAnswer(Long courseId, String question) {
        // 临时占位：从知识库简单检索
        var chunks = knowledgeChunkRepository.findByCourseId(courseId);
        if (chunks.isEmpty()) {
            return "该课程暂无知识库内容，请联系教师上传课程资料。";
        }
        return "关于「" + question + "」的问题，建议参考课程资料。RAG 回答功能待集成 Spring AI。";
    }
}
