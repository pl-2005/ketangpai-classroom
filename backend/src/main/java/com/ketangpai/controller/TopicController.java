package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.Topic;
import com.ketangpai.model.entity.TopicReply;
import com.ketangpai.model.enums.TopicStatus;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 话题讨论 Controller
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping("/courses/{courseId}/topics")
    public Result<List<Topic>> listByCourse(@CurrentUserId Long userId, @PathVariable Long courseId) {
        return Result.ok(topicService.listByCourse(courseId, userId));
    }

    @GetMapping("/topics/{topicId}")
    public Result<Map<String, Object>> getDetail(@CurrentUserId Long userId, @PathVariable Long topicId) {
        return Result.ok(Map.of(
                "topic", topicService.getDetail(topicId, userId),
                "replies", topicService.getReplies(topicId)
        ));
    }

    @PostMapping("/topics")
    public Result<Topic> create(@CurrentUserId Long userId, @RequestBody Map<String, Object> body) {
        return Result.ok(topicService.create(
                ((Number) body.get("courseId")).longValue(),
                userId,
                (String) body.get("title"),
                (String) body.get("content"),
                (Boolean) body.get("isAnonymous")));
    }

    @PostMapping("/topics/{topicId}/replies")
    public Result<TopicReply> reply(@CurrentUserId Long userId,
                                     @PathVariable Long topicId,
                                     @RequestBody Map<String, Object> body) {
        return Result.ok(topicService.reply(topicId, userId,
                (String) body.get("content"),
                (Boolean) body.get("isAnonymous"),
                body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null));
    }

    @PostMapping("/topics/{topicId}/status")
    public Result<Topic> updateStatus(@CurrentUserId Long userId,
                                       @PathVariable Long topicId,
                                       @RequestBody Map<String, String> body) {
        return Result.ok(topicService.updateStatus(topicId, userId,
                TopicStatus.valueOf(body.get("status"))));
    }

    @PutMapping("/topics/{topicId}")
    public Result<Topic> update(@CurrentUserId Long userId,
                                 @PathVariable Long topicId,
                                 @RequestBody Map<String, String> body) {
        return Result.ok(topicService.update(topicId, userId,
                body.get("title"), body.get("content")));
    }

    @DeleteMapping("/topics/{topicId}")
    public Result<Void> deleteTopic(@CurrentUserId Long userId, @PathVariable Long topicId) {
        topicService.deleteTopic(topicId, userId);
        return Result.ok();
    }

    @DeleteMapping("/topics/replies/{replyId}")
    public Result<Void> deleteReply(@CurrentUserId Long userId, @PathVariable Long replyId) {
        topicService.deleteReply(replyId, userId);
        return Result.ok();
    }
}
