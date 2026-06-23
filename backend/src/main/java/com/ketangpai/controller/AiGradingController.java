package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.AiGradingConfig;
import com.ketangpai.model.entity.AiGradingResult;
import com.ketangpai.model.enums.GradingStyle;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiGradingController {

    private final AiGradingService aiGradingService;

    @GetMapping("/assignments/{assignmentId}/ai-grading-config")
    public Result<AiGradingConfig> getConfig(@PathVariable Long assignmentId) {
        Long userId = 1L;
        return Result.ok(aiGradingService.getConfig(assignmentId, userId));
    }

    @PutMapping("/assignments/{assignmentId}/ai-grading-config")
    public Result<AiGradingConfig> updateConfig(@PathVariable Long assignmentId, @RequestBody Map<String, Object> body) {
        Long userId = 1L;
        return Result.ok(aiGradingService.updateConfig(assignmentId, userId,
                (Boolean) body.get("enabled"),
                (String) body.get("promptTemplate"),
                body.get("rubric") != null ? body.get("rubric").toString() : null,
                body.get("gradingStyle") != null ? GradingStyle.valueOf((String) body.get("gradingStyle")) : null));
    }

    @PostMapping("/submissions/{submissionId}/ai-grade")
    public Result<AiGradingResult> gradeSubmission(@PathVariable Long submissionId) {
        Long teacherId = 1L;
        return Result.ok(aiGradingService.gradeSubmission(submissionId, teacherId));
    }

    @PostMapping("/assignments/{assignmentId}/ai-grade-batch")
    public Result<Map<String, Object>> batchGrade(@PathVariable Long assignmentId) {
        Long teacherId = 1L;
        long count = aiGradingService.batchGrade(assignmentId, teacherId);
        return Result.ok(Map.of("totalCount", count));
    }
}
