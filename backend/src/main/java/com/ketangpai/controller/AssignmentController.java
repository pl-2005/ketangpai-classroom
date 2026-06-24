package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.dto.assignment.AssignmentListResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.List;
import java.util.Map;

/**
 * 作业管理 Controller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping("/courses/{courseId}/assignments")
    public Result<Page<AssignmentListResponse>> listByCourse(
            @CurrentUserId Long userId,
            @PathVariable Long courseId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return Result.ok(assignmentService.listByCourse(courseId, userId, status, pageable));
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
                null, // deadline TODO
                body.get("maxScore") != null ? ((Number) body.get("maxScore")).intValue() : null,
                body.get("allowResubmit") instanceof Boolean ? (Boolean) body.get("allowResubmit") : null,
                null // attachmentIds TODO
        ));
    }

    @PutMapping("/assignments/{assignmentId}")
    public Result<Assignment> update(@CurrentUserId Long userId,
                                      @PathVariable Long assignmentId,
                                      @RequestBody Map<String, Object> body) {
        return Result.ok(assignmentService.update(assignmentId, userId,
                (String) body.get("title"),
                (String) body.get("content"),
                null, // deadline TODO
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
}
