package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.AiGradingConfig;
import com.ketangpai.model.entity.AiGradingResult;
import com.ketangpai.model.enums.GradingStyle;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.AiGradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 批阅 Controller
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiGradingController {

    private final AiGradingService aiGradingService;

    @GetMapping("/assignments/{assignmentId}/ai-grading-config")
    public Result<AiGradingConfig> getConfig(@CurrentUserId Long userId, @PathVariable Long assignmentId) {
        return Result.ok(aiGradingService.getConfig(assignmentId, userId));
    }

    @PutMapping("/assignments/{assignmentId}/ai-grading-config")
    public Result<AiGradingConfig> updateConfig(@CurrentUserId Long userId,
                                                 @PathVariable Long assignmentId,
                                                 @RequestBody Map<String, Object> body) {
        return Result.ok(aiGradingService.updateConfig(assignmentId, userId,
                (Boolean) body.get("enabled"),
                (String) body.get("promptTemplate"),
                body.get("rubric") != null ? body.get("rubric").toString() : null,
                body.get("gradingStyle") != null ? GradingStyle.valueOf((String) body.get("gradingStyle")) : null));
    }

    @PostMapping("/submissions/{submissionId}/ai-grade")
    public Result<AiGradingResult> gradeSubmission(@CurrentUserId Long userId,
                                                    @PathVariable Long submissionId) {
        return Result.ok(aiGradingService.gradeSubmission(submissionId, userId));
    }

    @PostMapping("/assignments/{assignmentId}/ai-grade-batch")
    public Result<Map<String, Object>> batchGrade(@CurrentUserId Long userId,
                                                   @PathVariable Long assignmentId) {
        long count = aiGradingService.batchGrade(assignmentId, userId);
        return Result.ok(Map.of("totalCount", count));
    }
}
