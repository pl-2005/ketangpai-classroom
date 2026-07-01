package com.ketangpai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ketangpai.model.entity.ChatMessage;
import com.ketangpai.model.entity.KnowledgeChunk;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.ChatRole;
import com.ketangpai.repository.ChatMessageRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.KnowledgeChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 答疑机器人服务（RAG 架构）
 *
 * <p>核心流程：
 * <ol>
 *   <li>用户提问 → 向量化问题</li>
 *   <li>Qdrant 语义检索（courseId 强制过滤）</li>
 *   <li>加载对话历史 + 检索到的知识库块</li>
 *   <li>构建上下文 Prompt → LLM 生成回答（含引用来源）</li>
 *   <li>保存回答并填充 referencesJson</li>
 * </ol>
 */
@Slf4j
@Service
public class ChatService extends BaseService {

    /** 对话历史上限条数（用于构建 LLM 上下文） */
    private static final int MAX_HISTORY_MESSAGES = 10;

    /** RAG 检索返回数量 */
    private static final int TOP_K = 5;

    private final ChatMessageRepository chatMessageRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final ChatClient aiChatChatClient;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;

    public ChatService(CourseMemberRepository courseMemberRepository,
                       ChatMessageRepository chatMessageRepository,
                       KnowledgeChunkRepository knowledgeChunkRepository,
                       ChatClient aiChatChatClient,
                       KnowledgeBaseService knowledgeBaseService,
                       ObjectMapper objectMapper) {
        super(courseMemberRepository);
        this.chatMessageRepository = chatMessageRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.aiChatChatClient = aiChatChatClient;
        this.knowledgeBaseService = knowledgeBaseService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String createSession(Long courseId, Long userId) {
        getMemberOrThrow(courseId, userId);
        return UUID.randomUUID().toString();
    }

    /** 返回会话摘要列表（含标题和最后消息时间） */
    public List<Map<String, Object>> listSessions(Long courseId, Long userId) {
        getMemberOrThrow(courseId, userId);
        List<String> sessionIds = chatMessageRepository
                .findDistinctSessionsByUserIdAndCourseId(userId, courseId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (String sid : sessionIds) {
            Page<ChatMessage> page = chatMessageRepository
                    .findBySessionIdOrderByCreateTimeDesc(sid, Pageable.ofSize(1));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("sessionId", sid);
            if (!page.getContent().isEmpty()) {
                ChatMessage lastMsg = page.getContent().get(0);
                info.put("lastMessage", truncate(lastMsg.getContent(), 50));
                info.put("lastTime", lastMsg.getCreateTime() != null
                        ? lastMsg.getCreateTime().toString() : null);
            }
            // 用第一条用户消息作为标题
            String title = findSessionTitle(sid);
            info.put("title", title != null ? title : "新对话");
            result.add(info);
        }
        return result;
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

        // 2. RAG 检索 + LLM 生成回答
        String answer;
        String referencesJson = null;
        try {
            // 2a. 检索相关课程内容（强制 courseId 过滤）
            List<KnowledgeChunk> relevantChunks = knowledgeBaseService
                    .searchRelevant(courseId, question, TOP_K);

            // 2b. 加载对话历史
            List<ChatMessage> history = loadRecentHistory(sessionId);

            // 2c. 构建 LLM Prompt 并调用
            answer = generateRagAnswer(question, relevantChunks, history);

            // 2d. 构建引用来源 JSON
            if (!relevantChunks.isEmpty()) {
                referencesJson = buildReferencesJson(relevantChunks);
            }
        } catch (Exception e) {
            log.error("RAG 回答生成失败: courseId={}, question={}", courseId, question, e);
            answer = "抱歉，AI 服务暂时不可用，请稍后重试。\n\n错误信息：" + e.getMessage();
        }

        // 3. 保存 AI 回答
        ChatMessage assistantMsg = ChatMessage.builder()
                .userId(userId)
                .courseId(courseId)
                .sessionId(sessionId)
                .role(ChatRole.ASSISTANT)
                .content(answer)
                .referencesJson(referencesJson)
                .build();

        return chatMessageRepository.save(assistantMsg);
    }

    /**
     * 流式 AI 答疑（SSE 模式）。
     * 保存用户消息到 DB，通过 RAG + LLM 流式生成回答，
     * 返回 Flux<String> 供 Controller 层桥接到 SseEmitter。
     * <p>
     * 注意：ASSISTANT 消息的最终保存由 Controller 在流结束后执行。
     */
    @Transactional
    public Flux<String> chatStream(Long courseId, Long userId,
                                   String sessionId, String question,
                                   List<KnowledgeChunk>[] outChunks,
                                   String[] outReferencesJson) {
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

        // 2. RAG 检索
        List<KnowledgeChunk> relevantChunks;
        try {
            relevantChunks = knowledgeBaseService.searchRelevant(courseId, question, TOP_K);
        } catch (Exception e) {
            log.error("RAG 检索失败", e);
            relevantChunks = List.of();
        }
        outChunks[0] = relevantChunks;

        // 3. 构建引用 JSON（在流开始前计算好）
        if (!relevantChunks.isEmpty()) {
            outReferencesJson[0] = buildReferencesJson(relevantChunks);
        }

        // 4. 加载对话历史
        List<ChatMessage> history = loadRecentHistory(sessionId);

        // 5. 构建 Prompt 并流式调用 LLM
        String systemPrompt = buildSystemPromptWithContext(relevantChunks);
        String userPrompt = buildUserPromptWithHistory(question, history);

        try {
            return aiChatChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("LLM 流式调用失败", e);
            // 降级：返回降级回答作为单个 token
            String fallback;
            if (!relevantChunks.isEmpty()) {
                fallback = buildFallbackAnswer(question, relevantChunks);
            } else {
                fallback = "抱歉，AI 服务暂时不可用。请确认课程已配置知识库，或联系教师。";
            }
            return Flux.just(fallback);
        }
    }

    /** 获取指定会话的对话历史（分页） */
    public Page<ChatMessage> getHistory(Long courseId, String sessionId, Long userId, Pageable pageable) {
        getMemberOrThrow(courseId, userId);
        if (!isSessionParticipant(sessionId, userId)) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return chatMessageRepository.findBySessionIdOrderByCreateTimeDesc(sessionId, pageable);
    }

    @Transactional
    public void deleteSession(String sessionId, Long userId) {
        if (!isSessionParticipant(sessionId, userId)) {
            throw new BusinessException(403, "无权删除该会话");
        }
        chatMessageRepository.deleteBySessionId(sessionId);
    }

    // ==================== RAG 核心逻辑 ====================

    /** 基于 RAG 生成回答 */
    private String generateRagAnswer(String question,
                                     List<KnowledgeChunk> relevantChunks,
                                     List<ChatMessage> history) {
        // 构建系统提示（含课程资料上下文）
        String systemPrompt = buildSystemPromptWithContext(relevantChunks);

        // 构建用户提示（含历史对话 + 当前问题）
        String userPrompt = buildUserPromptWithHistory(question, history);

        try {
            return aiChatChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            // 降级：无 LLM 时的 RAG 摘要
            if (!relevantChunks.isEmpty()) {
                return buildFallbackAnswer(question, relevantChunks);
            }
            return "抱歉，AI 服务暂时不可用。请确认课程已配置知识库，或联系教师。";
        }
    }

    /** 构建含课程资料上下文的 System Prompt */
    private String buildSystemPromptWithContext(List<KnowledgeChunk> relevantChunks) {
        StringBuilder sb = new StringBuilder();
        // base system prompt 已在 ChatClient Bean 中设置，这里追加上下文
        sb.append("以下是本课程的相关资料内容，请据此回答学生问题：\n\n");

        if (relevantChunks.isEmpty()) {
            sb.append("（课程知识库中暂无与该问题直接相关的内容。）\n");
        } else {
            // 按来源分组展示
            Map<String, List<KnowledgeChunk>> grouped = relevantChunks.stream()
                    .collect(Collectors.groupingBy(
                            c -> c.getSourceName() != null ? c.getSourceName() : "未知来源",
                            LinkedHashMap::new,
                            Collectors.toList()));

            int refIndex = 1;
            for (Map.Entry<String, List<KnowledgeChunk>> entry : grouped.entrySet()) {
                for (KnowledgeChunk chunk : entry.getValue()) {
                    sb.append("--- [").append(refIndex).append("] 来源：")
                            .append(entry.getKey()).append(" ---\n");
                    sb.append(chunk.getContent()).append("\n\n");
                    refIndex++;
                }
            }
        }

        sb.append("\n请根据以上资料回答学生的问题。引用时请使用 [来源N] 的格式标注。");
        return sb.toString();
    }

    /** 构建含历史对话的 User Prompt */
    private String buildUserPromptWithHistory(String question, List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();

        if (!history.isEmpty()) {
            sb.append("对话历史：\n");
            for (ChatMessage msg : history) {
                String roleLabel = msg.getRole() == ChatRole.USER ? "学生" : "助教";
                sb.append(roleLabel).append("：").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("学生当前问题：").append(question);
        return sb.toString();
    }

    /** LLM 不可用时的降级回答：拼接检索到的资料片段 */
    private String buildFallbackAnswer(String question, List<KnowledgeChunk> relevantChunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("关于「").append(question).append("」，课程资料中有以下相关内容：\n\n");

        Map<String, List<KnowledgeChunk>> grouped = relevantChunks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getSourceName() != null ? c.getSourceName() : "未知来源",
                        LinkedHashMap::new,
                        Collectors.toList()));

        int idx = 1;
        for (Map.Entry<String, List<KnowledgeChunk>> entry : grouped.entrySet()) {
            sb.append("**").append(idx).append(". ").append(entry.getKey()).append("**\n");
            for (KnowledgeChunk chunk : entry.getValue()) {
                sb.append("> ").append(truncate(chunk.getContent(), 200)).append("\n");
            }
            sb.append("\n");
            idx++;
        }

        sb.append("\n> ⚠️ AI 服务暂时不可用，以上为知识库检索结果摘要。");
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    /** 加载会话的最近 N 条历史消息（从旧到新） */
    private List<ChatMessage> loadRecentHistory(String sessionId) {
        Page<ChatMessage> page = chatMessageRepository
                .findBySessionIdOrderByCreateTimeDesc(sessionId,
                        PageRequest.of(0, MAX_HISTORY_MESSAGES));
        // 反转为从旧到新的顺序
        List<ChatMessage> list = new ArrayList<>(page.getContent());
        java.util.Collections.reverse(list);
        return list;
    }

    /** 构建引用来源 JSON */
    private String buildReferencesJson(List<KnowledgeChunk> chunks) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("chunkId", chunk.getId());
            ref.put("sourceName", chunk.getSourceName());
            ref.put("sourceType", chunk.getSourceType().name());
            ref.put("excerpt", truncate(chunk.getContent(), 150));
            refs.add(ref);
        }
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (JsonProcessingException e) {
            log.warn("序列化 referencesJson 失败", e);
            return "[]";
        }
    }

    /** 查找会话的第一条用户消息作为标题 */
    private String findSessionTitle(String sessionId) {
        Page<ChatMessage> page = chatMessageRepository
                .findBySessionIdOrderByCreateTimeDesc(sessionId, Pageable.ofSize(50));
        // 从最后（最早）找第一条 USER 消息
        List<ChatMessage> all = new ArrayList<>(page.getContent());
        java.util.Collections.reverse(all);
        for (ChatMessage msg : all) {
            if (msg.getRole() == ChatRole.USER) {
                return truncate(msg.getContent(), 30);
            }
        }
        return null;
    }

    /** 检查用户是否参与了指定会话（新会话无消息时允许访问） */
    private boolean isSessionParticipant(String sessionId, Long userId) {
        Page<ChatMessage> page = chatMessageRepository
                .findBySessionIdOrderByCreateTimeDesc(sessionId, Pageable.ofSize(1));
        // 新创建的会话还没有消息，视为可访问（调用方已校验课程成员身份）
        if (page.getContent().isEmpty()) {
            return true;
        }
        return page.getContent().stream().anyMatch(msg -> msg.getUserId().equals(userId));
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
