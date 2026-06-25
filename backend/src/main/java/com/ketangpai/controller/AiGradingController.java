package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.dto.ai.UpdateAiGradingConfigRequest;
import com.ketangpai.model.entity.AiGradingConfig;
import com.ketangpai.model.entity.AiGradingResult;
import com.ketangpai.model.entity.GradingBatchTask;
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

import java.util.List;

/**
 * AI 批阅 Controller
 */
@RestController
@RequestMapping("/api")
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
                                                 @RequestBody UpdateAiGradingConfigRequest body) {
        return Result.ok(aiGradingService.updateConfig(assignmentId, userId,
                body.enabled(), body.promptTemplate(), body.rubricJson(), body.gradingStyle()));
    }

    /** 手动触发单份 AI 批阅（同步返回结果） */
    @PostMapping("/submissions/{submissionId}/ai-grade")
    public Result<AiGradingResult> gradeSubmission(@CurrentUserId Long userId,
                                                    @PathVariable Long submissionId) {
        return Result.ok(aiGradingService.gradeSubmission(submissionId, userId));
    }

    /** 批量触发 AI 批阅（异步执行，返回任务信息供轮询） */
    @PostMapping("/assignments/{assignmentId}/ai-grade-batch")
    public Result<GradingBatchTask> batchGrade(@CurrentUserId Long userId,
                                                @PathVariable Long assignmentId) {
        return Result.ok(aiGradingService.batchGrade(assignmentId, userId));
    }

    /** 查询批量任务列表 */
    @GetMapping("/assignments/{assignmentId}/ai-grade-batch/status")
    public Result<List<GradingBatchTask>> getBatchStatus(@CurrentUserId Long userId,
                                                          @PathVariable Long assignmentId) {
        return Result.ok(aiGradingService.getBatchTasks(assignmentId, userId));
    }

    /** 查询单个批量任务详情 */
    @GetMapping("/ai-grade-batch/{taskId}")
    public Result<GradingBatchTask> getTaskDetail(@CurrentUserId Long userId,
                                                   @PathVariable Long taskId) {
        return Result.ok(aiGradingService.getTaskDetail(taskId, userId));
    }
}
