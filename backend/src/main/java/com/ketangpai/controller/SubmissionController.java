package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.Submission;
import com.ketangpai.repository.AiGradingResultRepository;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提交管理 Controller
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final AiGradingResultRepository aiGradingResultRepository;

    @PostMapping("/assignments/{assignmentId}/submit")
    public Result<Submission> submit(@CurrentUserId Long userId,
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> fileIdNumbers = (List<Number>) body.get("fileIds");
        List<Long> fileIds = fileIdNumbers != null
                ? fileIdNumbers.stream().map(Number::longValue).toList()
                : null;
        return Result.ok(submissionService.submit(assignmentId, userId,
                (String) body.get("content"), fileIds));
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    public Result<List<Submission>> listByAssignment(@CurrentUserId Long userId,
            @PathVariable Long assignmentId,
            @RequestParam(required = false) String status) {
        return Result.ok(submissionService.listByAssignment(assignmentId, userId, status));
    }

    @GetMapping("/submissions/{submissionId}")
    public Result<Map<String, Object>> getDetail(@CurrentUserId Long userId,
            @PathVariable Long submissionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submission", submissionService.getDetail(submissionId, userId));
        result.put("files", submissionService.getFiles(submissionId));
        result.put("aiGradingResult", aiGradingResultRepository.findBySubmissionId(submissionId).orElse(null));
        return Result.ok(result);
    }

    @PutMapping("/submissions/{submissionId}/grade")
    public Result<Submission> grade(@CurrentUserId Long userId,
            @PathVariable Long submissionId,
            @RequestBody Map<String, Object> body) {
        return Result.ok(submissionService.grade(submissionId, userId,
                body.get("score") != null ? ((Number) body.get("score")).intValue() : null,
                (String) body.get("teacherComment")));
    }

    @PostMapping("/submissions/{submissionId}/return")
    public Result<Submission> returnSubmission(@CurrentUserId Long userId,
            @PathVariable Long submissionId,
            @RequestBody Map<String, String> body) {
        return Result.ok(submissionService.returnSubmission(submissionId, userId, body.get("reason")));
    }
}
