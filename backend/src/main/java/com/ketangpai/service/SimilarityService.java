package com.ketangpai.service;

import com.ketangpai.model.entity.SimilarityPair;
import com.ketangpai.model.entity.SimilarityReport;
import com.ketangpai.model.entity.Submission;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.SimilarityPairRepository;
import com.ketangpai.repository.SimilarityReportRepository;
import com.ketangpai.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 作业相似度分析服务
 */
@Service
public class SimilarityService extends BaseService {

    private final SimilarityReportRepository reportRepository;
    private final SimilarityPairRepository pairRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;

    public SimilarityService(CourseMemberRepository courseMemberRepository,
                             SimilarityReportRepository reportRepository,
                             SimilarityPairRepository pairRepository,
                             SubmissionRepository submissionRepository,
                             AssignmentRepository assignmentRepository) {
        super(courseMemberRepository);
        this.reportRepository = reportRepository;
        this.pairRepository = pairRepository;
        this.submissionRepository = submissionRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public SimilarityReport analyze(Long assignmentId, Long teacherId, BigDecimal threshold) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), teacherId);

        List<Submission> submissions = submissionRepository.findSubmittedByAssignmentId(assignmentId);
        if (submissions.size() < 2) {
            throw new BusinessException(400, "至少需要 2 份提交才能进行相似度分析");
        }

        BigDecimal th = threshold != null ? threshold : new BigDecimal("0.80");

        // 创建报告
        SimilarityReport report = SimilarityReport.builder()
                .assignmentId(assignmentId)
                .totalSubmissions(submissions.size())
                .threshold(th)
                .generatedAt(LocalDateTime.now())
                .build();
        report = reportRepository.save(report);

        // TODO: 实际 AI 实现
        //   1. 用 EmbeddingClient 向量化每份提交
        //   2. 计算两两余弦相似度矩阵
        //   3. 超阈值的对持久化到 similarity_pair
        //   4. 提取相似段落高亮信息

        return report;
    }

    public List<SimilarityReport> getReports(Long assignmentId, Long userId) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        getMemberOrThrow(assignment.getCourseId(), userId);
        return reportRepository.findByAssignmentIdOrderByGeneratedAtDesc(assignmentId);
    }

    public Map<String, Object> getReportDetail(Long reportId, Long userId) {
        SimilarityReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(404, "报告不存在"));
        var assignment = assignmentRepository.findById(report.getAssignmentId())
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        List<SimilarityPair> pairs = pairRepository
                .findByReportIdAndSimilarityScoreGreaterThanEqualOrderBySimilarityScoreDesc(
                        reportId, report.getThreshold());

        return Map.of("report", report, "pairs", pairs);
    }
}
