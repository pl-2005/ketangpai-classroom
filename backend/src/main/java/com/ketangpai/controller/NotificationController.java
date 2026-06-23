package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知管理 Controller
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<?> list(@CurrentUserId Long userId,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(notificationService.list(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> getUnreadCount(@CurrentUserId Long userId) {
        return Result.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @PutMapping("/{notificationId}/read")
    public Result<Void> markRead(@CurrentUserId Long userId, @PathVariable Long notificationId) {
        notificationService.markRead(notificationId, userId);
        return Result.ok();
    }

    @PutMapping("/read-all")
    public Result<Map<String, Integer>> markAllRead(@CurrentUserId Long userId) {
        int count = notificationService.markAllRead(userId);
        return Result.ok(Map.of("affected", count));
    }

    @DeleteMapping("/{notificationId}")
    public Result<Void> delete(@CurrentUserId Long userId, @PathVariable Long notificationId) {
        notificationService.delete(notificationId, userId);
        return Result.ok();
    }
}
