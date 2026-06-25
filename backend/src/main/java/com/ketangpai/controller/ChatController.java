package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.entity.ChatMessage;
import com.ketangpai.model.entity.Material;
import com.ketangpai.model.entity.Topic;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.MaterialRepository;
import com.ketangpai.repository.TopicRepository;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.ChatService;
import com.ketangpai.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 答疑 Controller — 支持 RAG 架构的多轮对话与知识库管理。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final MaterialRepository materialRepository;
    private final AssignmentRepository assignmentRepository;
    private final TopicRepository topicRepository;

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

    /** 发送消息并获取 AI 回答（RAG 架构） */
    @PostMapping("/courses/{courseId}/ai-chat")
    public Result<ChatMessage> chat(@CurrentUserId Long userId,
                                     @PathVariable Long courseId,
                                     @RequestBody Map<String, String> body) {
        return Result.ok(chatService.chat(courseId, userId,
                body.get("sessionId"), body.get("content")));
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
