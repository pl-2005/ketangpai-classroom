package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.SimilarityReport;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.SimilarityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 相似度分析 Controller
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SimilarityController {

    private final SimilarityService similarityService;

    @PostMapping("/assignments/{assignmentId}/similarity/analyze")
    public Result<SimilarityReport> analyze(@CurrentUserId Long userId,
                                             @PathVariable Long assignmentId,
                                             @RequestBody Map<String, Object> body) {
        BigDecimal threshold = body.get("threshold") != null
                ? new BigDecimal(body.get("threshold").toString()) : null;
        return Result.ok(similarityService.analyze(assignmentId, userId, threshold));
    }

    @GetMapping("/assignments/{assignmentId}/similarity/reports")
    public Result<List<SimilarityReport>> getReports(@CurrentUserId Long userId,
                                                      @PathVariable Long assignmentId) {
        return Result.ok(similarityService.getReports(assignmentId, userId));
    }

    @GetMapping("/similarity/reports/{reportId}")
    public Result<Map<String, Object>> getReportDetail(@CurrentUserId Long userId,
                                                        @PathVariable Long reportId) {
        return Result.ok(similarityService.getReportDetail(reportId, userId));
    }
}
