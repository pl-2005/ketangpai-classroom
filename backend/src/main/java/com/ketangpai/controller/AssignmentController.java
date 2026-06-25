package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.enums.AssignmentStatus;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 作业管理 Controller
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping("/courses/{courseId}/assignments")
    public Result<List<Assignment>> listByCourse(@CurrentUserId Long userId,
                                                  @PathVariable Long courseId,
                                                  @RequestParam(required = false) String status) {
        return Result.ok(assignmentService.listByCourse(courseId, userId, status));
    }

    @GetMapping("/assignments/{assignmentId}")
    public Result<Map<String, Object>> getDetail(@CurrentUserId Long userId,
                                                  @PathVariable Long assignmentId) {
        Assignment assignment = assignmentService.getDetail(assignmentId, userId);
        return Result.ok(Map.of(
                "assignment", assignment,
                "attachments", assignmentService.getAttachments(assignmentId)
        ));
    }

    @PostMapping("/assignments")
    public Result<Assignment> create(@CurrentUserId Long userId, @RequestBody Map<String, Object> body) {
        return Result.ok(assignmentService.create(userId,
                (String) body.get("title"),
                (String) body.get("content"),
                body.get("courseId") instanceof Integer ? ((Integer) body.get("courseId")).longValue() : (Long) body.get("courseId"),
                parseDateTime(body.get("deadline")),
                body.get("maxScore") != null ? ((Number) body.get("maxScore")).intValue() : null,
                body.get("allowResubmit") instanceof Boolean ? (Boolean) body.get("allowResubmit") : null,
                parseLongList(body.get("attachmentIds"))
        ));
    }

    @PutMapping("/assignments/{assignmentId}")
    public Result<Assignment> update(@CurrentUserId Long userId,
                                      @PathVariable Long assignmentId,
                                      @RequestBody Map<String, Object> body) {
        return Result.ok(assignmentService.update(assignmentId, userId,
                (String) body.get("title"),
                (String) body.get("content"),
                parseDateTime(body.get("deadline")),
                body.get("maxScore") != null ? ((Number) body.get("maxScore")).intValue() : null,
                body.get("allowResubmit") instanceof Boolean ? (Boolean) body.get("allowResubmit") : null
        ));
    }

    @PostMapping("/assignments/{assignmentId}/status")
    public Result<Assignment> updateStatus(@CurrentUserId Long userId,
                                            @PathVariable Long assignmentId,
                                            @RequestBody Map<String, String> body) {
        return Result.ok(assignmentService.updateStatus(assignmentId, userId,
                AssignmentStatus.valueOf(body.get("status"))));
    }

    @PostMapping("/assignments/{assignmentId}/urge")
    public Result<Map<String, Object>> urge(@CurrentUserId Long userId,
                                             @PathVariable Long assignmentId,
                                             @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> studentIds = (List<Long>) body.get("studentIds");
        long count = assignmentService.urge(assignmentId, userId, studentIds);
        return Result.ok(Map.of("urgedCount", count));
    }

    // ==================== 参数解析辅助方法 ====================

    private static LocalDateTime parseDateTime(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            // 尝试 "yyyy-MM-dd HH:mm:ss"
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Long> parseLongList(Object value) {
        if (value == null) return null;
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> item instanceof Number ? ((Number) item).longValue() : null)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return null;
    }
}
