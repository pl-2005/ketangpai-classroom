package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.Submission;
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

    @PostMapping("/assignments/{assignmentId}/submit")
    public Result<Submission> submit(@PathVariable Long assignmentId, @RequestBody Map<String, Object> body) {
        Long studentId = 1L;
        @SuppressWarnings("unchecked")
        List<Long> fileIds = (List<Long>) body.get("fileIds");
        return Result.ok(submissionService.submit(assignmentId, studentId,
                (String) body.get("content"), fileIds));
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    public Result<List<Submission>> listByAssignment(@PathVariable Long assignmentId,
                                                      @RequestParam(required = false) String status) {
        Long teacherId = 1L;
        return Result.ok(submissionService.listByAssignment(assignmentId, teacherId, status));
    }

    @GetMapping("/submissions/{submissionId}")
    public Result<Map<String, Object>> getDetail(@PathVariable Long submissionId) {
        Long userId = 1L;
        return Result.ok(Map.of(
                "submission", submissionService.getDetail(submissionId, userId),
                "files", submissionService.getFiles(submissionId)
        ));
    }

    @PutMapping("/submissions/{submissionId}/grade")
    public Result<Submission> grade(@PathVariable Long submissionId, @RequestBody Map<String, Object> body) {
        Long teacherId = 1L;
        return Result.ok(submissionService.grade(submissionId, teacherId,
                body.get("score") != null ? ((Number) body.get("score")).intValue() : null,
                (String) body.get("teacherComment")));
    }

    @PostMapping("/submissions/{submissionId}/return")
    public Result<Submission> returnSubmission(@PathVariable Long submissionId, @RequestBody Map<String, String> body) {
        Long teacherId = 1L;
        return Result.ok(submissionService.returnSubmission(submissionId, teacherId, body.get("reason")));
    }
}
