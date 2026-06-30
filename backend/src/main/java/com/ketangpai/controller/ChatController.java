package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.entity.ChatMessage;
import com.ketangpai.model.entity.KnowledgeChunk;
import com.ketangpai.model.entity.Material;
import com.ketangpai.model.entity.Topic;
import com.ketangpai.model.enums.ChatRole;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.ChatMessageRepository;
import com.ketangpai.repository.MaterialRepository;
import com.ketangpai.repository.TopicRepository;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.ChatService;
import com.ketangpai.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 答疑 Controller — 支持 RAG 架构的多轮对话与知识库管理。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final MaterialRepository materialRepository;
    private final AssignmentRepository assignmentRepository;
    private final TopicRepository topicRepository;
    private final ChatMessageRepository chatMessageRepository;

    /** 创建新会话，返回 sessionId */
    @PostMapping("/courses/{courseId}/ai-chat/sessions")
    public Result<Map<String, String>> createSession(@CurrentUserId Long userId,
                                                      @PathVariable Long courseId) {
        return Result.ok(Map.of("sessionId", chatService.createSession(courseId, userId)));
    }

    /** 列出当前用户在课程中的所有会话摘要 */
    @GetMapping("/courses/{courseId}/ai-chat/sessions")
    public Result<List<Map<String, Object>>> listSessions(@CurrentUserId Long userId,
                                                           @PathVariable Long courseId) {
        return Result.ok(chatService.listSessions(courseId, userId));
    }

    /** 发送消息并获取 AI 回答（RAG 架构，同步模式） */
    @PostMapping("/courses/{courseId}/ai-chat")
    public Result<ChatMessage> chat(@CurrentUserId Long userId,
                                     @PathVariable Long courseId,
                                     @RequestBody Map<String, String> body) {
        return Result.ok(chatService.chat(courseId, userId,
                body.get("sessionId"), body.get("content")));
    }

    /**
     * 发送消息并获取 AI 流式回答（SSE）。
     * 事件格式：start → chunk* → references → done
     */
    @PostMapping(value = "/courses/{courseId}/ai-chat/stream",
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@CurrentUserId Long userId,
                                  @PathVariable Long courseId,
                                  @RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String content = body.get("content");
        if (sessionId == null || content == null) {
            throw new com.ketangpai.exception.BusinessException(400, "sessionId 和 content 不能为空");
        }

        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

        // 在独立线程中桥接 Flux → SseEmitter
        CompletableFuture.runAsync(() -> {
            try {
                // 发送 start 事件
                emitter.send(SseEmitter.event()
                        .name("start")
                        .data(Map.of("sessionId", sessionId)));

                // 用于收集完整回答文本
                StringBuilder fullContent = new StringBuilder();
                List<KnowledgeChunk>[] chunksHolder = new List[]{List.of()};
                String[] refsHolder = new String[]{null};

                // 订阅流式回答
                chatService.chatStream(courseId, userId, sessionId, content,
                                chunksHolder, refsHolder)
                        .doOnNext(token -> {
                            try {
                                fullContent.append(token);
                                emitter.send(SseEmitter.event()
                                        .name("chunk")
                                        .data(Map.of("content", token)));
                            } catch (Exception e) {
                                log.warn("SSE chunk 发送失败", e);
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                // 发送 references 事件
                                if (refsHolder[0] != null) {
                                    List<Map<String, Object>> refs =
                                            parseReferencesJson(refsHolder[0]);
                                    emitter.send(SseEmitter.event()
                                            .name("references")
                                            .data(Map.of("references", (Object) refs)));
                                }

                                // 保存 ASSISTANT 消息到 DB
                                ChatMessage assistantMsg = ChatMessage.builder()
                                        .userId(userId)
                                        .courseId(courseId)
                                        .sessionId(sessionId)
                                        .role(ChatRole.ASSISTANT)
                                        .content(fullContent.toString())
                                        .referencesJson(refsHolder[0])
                                        .build();
                                assistantMsg = chatMessageRepository.save(assistantMsg);

                                // 发送 done 事件（含 messageId）
                                emitter.send(SseEmitter.event()
                                        .name("done")
                                        .data(Map.of("messageId", assistantMsg.getId())));
                                emitter.complete();
                            } catch (Exception e) {
                                log.error("SSE 完成事件发送失败", e);
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(error -> {
                            log.error("LLM 流式生成出错", error);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(Map.of("message", "AI 服务暂时不可用: "
                                                + error.getMessage())));
                            } catch (Exception ignored) {
                                // ignore
                            }
                            emitter.completeWithError(error);
                        })
                        .subscribe(); // 触发执行
            } catch (Exception e) {
                log.error("SSE 流式连接异常", e);
                emitter.completeWithError(e);
            }
        });

        // 注册超时和异常回调
        emitter.onTimeout(() -> log.warn("SSE 连接超时: sessionId={}", sessionId));
        emitter.onError(e -> log.error("SSE 连接异常: sessionId={}", sessionId, e));

        return emitter;
    }

    /** 解析 referencesJson 字符串为 List<Map>，供 SSE references 事件使用 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseReferencesJson(String json) {
        if (json == null) return List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            log.warn("解析 referencesJson 失败", e);
            return List.of();
        }
    }

    /** 获取会话历史（分页，最新在前） */
    @GetMapping("/courses/{courseId}/ai-chat/sessions/{sessionId}")
    public Result<?> getHistory(@CurrentUserId Long userId,
                                 @PathVariable Long courseId,
                                 @PathVariable String sessionId,
                                 @PageableDefault(size = 50) Pageable pageable) {
        return Result.ok(chatService.getHistory(courseId, sessionId, userId, pageable));
    }

    /** 删除会话 */
    @DeleteMapping("/courses/{courseId}/ai-chat/sessions/{sessionId}")
    public Result<Void> deleteSession(@CurrentUserId Long userId,
                                       @PathVariable Long courseId,
                                       @PathVariable String sessionId) {
        chatService.deleteSession(sessionId, userId);
        return Result.ok();
    }

    /** 重建课程知识库（教师权限） */
    @PostMapping("/courses/{courseId}/ai-chat/knowledge/rebuild")
    public Result<Map<String, String>> rebuildKnowledge(@CurrentUserId Long userId,
                                                        @PathVariable Long courseId) {
        List<Material> materials = materialRepository.findByCourseIdOrderBySortOrder(courseId);
        List<Assignment> assignments = assignmentRepository.findByCourseIdOrderByDeadlineAsc(courseId);
        List<Topic> topics = topicRepository.findByCourseIdOrderByStatusDescCreateTimeDesc(courseId);

        knowledgeBaseService.rebuildCourseKnowledge(courseId, materials, assignments, topics);
        return Result.ok(Map.of("message", "知识库重建已触发，请稍后查看"));
    }
}
