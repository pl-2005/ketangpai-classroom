package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.ChatMessage;
import com.ketangpai.service.ChatService;
import lombok.RequiredArgsConstructor;
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
 * AI 答疑 Controller
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/courses/{courseId}/ai-chat/sessions")
    public Result<Map<String, String>> createSession(@PathVariable Long courseId) {
        Long userId = 1L;
        return Result.ok(Map.of("sessionId", chatService.createSession(courseId, userId)));
    }

    @GetMapping("/courses/{courseId}/ai-chat/sessions")
    public Result<List<String>> listSessions(@PathVariable Long courseId) {
        Long userId = 1L;
        return Result.ok(chatService.listSessions(courseId, userId));
    }

    @PostMapping("/courses/{courseId}/ai-chat")
    public Result<ChatMessage> chat(@PathVariable Long courseId, @RequestBody Map<String, String> body) {
        Long userId = 1L;
        return Result.ok(chatService.chat(courseId, userId,
                body.get("sessionId"), body.get("content")));
    }

    @GetMapping("/courses/{courseId}/ai-chat/sessions/{sessionId}")
    public Result<?> getHistory(@PathVariable Long courseId, @PathVariable String sessionId) {
        Long userId = 1L;
        // TODO: 添加分页支持
        return Result.ok(chatService.listSessions(courseId, userId));
    }

    @DeleteMapping("/courses/{courseId}/ai-chat/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        Long userId = 1L;
        chatService.deleteSession(sessionId, userId);
        return Result.ok();
    }
}
