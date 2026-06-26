package com.ketangpai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.entity.SimilarityPair;
import com.ketangpai.model.entity.SimilarityReport;
import com.ketangpai.model.entity.Submission;
import com.ketangpai.model.entity.SubmissionFile;
import com.ketangpai.model.entity.User;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.SimilarityPairRepository;
import com.ketangpai.repository.SimilarityReportRepository;
import com.ketangpai.repository.SubmissionFileRepository;
import com.ketangpai.repository.SubmissionRepository;
import com.ketangpai.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 作业相似度分析服务
 *
 * <p>使用 EmbeddingModel 向量化提交文本，计算两两余弦相似度，
 * 对超过阈值的提交对标记疑似抄袭并提取相似段落。
 */
@Slf4j
@Service
public class SimilarityService extends BaseService {

    /** 单段文本最大字符数（用于段落级 Embedding） */
    private static final int SEGMENT_MAX_CHARS = 300;

    /** 段落最小字符数（过短的段落不参与段落级比对） */
    private static final int MIN_SEGMENT_LENGTH = 20;

    /** 相似段落返回数量上限 */
    private static final int MAX_HIGHLIGHTED_SEGMENTS = 5;

    /** 文本提取最大字符数（与 TextExtractionService 一致） */
    private static final int MAX_TEXT_LENGTH = 30_000;

    private final SimilarityReportRepository reportRepository;
    private final SimilarityPairRepository pairRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final UserRepository userRepository;
    private final EmbeddingModel embeddingModel;
    private final TextExtractionService textExtractionService;
    private final ObjectMapper objectMapper;

    public SimilarityService(CourseMemberRepository courseMemberRepository,
                             SimilarityReportRepository reportRepository,
                             SimilarityPairRepository pairRepository,
                             SubmissionRepository submissionRepository,
                             AssignmentRepository assignmentRepository,
                             SubmissionFileRepository submissionFileRepository,
                             UserRepository userRepository,
                             EmbeddingModel embeddingModel,
                             TextExtractionService textExtractionService,
                             ObjectMapper objectMapper) {
        super(courseMemberRepository);
        this.reportRepository = reportRepository;
        this.pairRepository = pairRepository;
        this.submissionRepository = submissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionFileRepository = submissionFileRepository;
        this.userRepository = userRepository;
        this.embeddingModel = embeddingModel;
        this.textExtractionService = textExtractionService;
        this.objectMapper = objectMapper;
    }

    // ==================== 分析入口 ====================

    /**
     * 对作业的全部提交执行语义相似度分析。
     *
     * @param assignmentId 作业 ID
     * @param teacherId    教师 ID（权限校验）
     * @param threshold    相似度阈值（0~1），null 则使用默认值 0.80
     * @return 相似度报告（含 suspiciousCount）
     */
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

        log.info("开始相似度分析: assignmentId={}, submissions={}, threshold={}",
                assignmentId, submissions.size(), th);

        // 1. 提取并标准化所有提交文本
        Map<Long, String> submissionTexts = extractAllTexts(submissions);
        List<Long> subIds = new ArrayList<>(submissionTexts.keySet());

        // 2. 批量生成 Embedding
        List<String> texts = subIds.stream().map(submissionTexts::get).collect(Collectors.toList());
        List<float[]> embeddings = batchEmbed(texts);

        log.info("Embedding 生成完成: count={}", embeddings.size());

        // 3. 计算两两余弦相似度，找出超阈值对
        List<SimilarityPair> suspiciousPairs = new ArrayList<>();

        for (int i = 0; i < subIds.size(); i++) {
            for (int j = i + 1; j < subIds.size(); j++) {
                double score = cosineSimilarity(embeddings.get(i), embeddings.get(j));
                BigDecimal scoreBd = BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);

                if (scoreBd.compareTo(th) >= 0) {
                    Long idA = subIds.get(i);
                    Long idB = subIds.get(j);

                    // 4. 查找相似段落
                    String textA = submissionTexts.get(idA);
                    String textB = submissionTexts.get(idB);
                    List<Map<String, Object>> segments = findSimilarPassages(textA, textB);

                    SimilarityPair pair = SimilarityPair.builder()
                            .reportId(0L) // 待报告保存后回填
                            .submissionAId(idA)
                            .submissionBId(idB)
                            .similarityScore(scoreBd)
                            .highlightedSegments(toJson(segments))
                            .build();
                    suspiciousPairs.add(pair);

                    log.debug("相似对: submissionA={}, submissionB={}, score={}",
                            idA, idB, scoreBd);
                }
            }
        }

        // 按相似度降序排列
        suspiciousPairs.sort(Comparator.comparing(SimilarityPair::getSimilarityScore).reversed());

        // 5. 创建报告
        SimilarityReport report = SimilarityReport.builder()
                .assignmentId(assignmentId)
                .totalSubmissions(submissions.size())
                .threshold(th)
                .suspiciousCount(suspiciousPairs.size())
                .generatedAt(LocalDateTime.now())
                .build();
        report = reportRepository.save(report);

        // 6. 回填 reportId 并批量保存相似对
        for (SimilarityPair pair : suspiciousPairs) {
            pair.setReportId(report.getId());
        }
        if (!suspiciousPairs.isEmpty()) {
            pairRepository.saveAll(suspiciousPairs);
        }

        log.info("相似度分析完成: reportId={}, suspiciousCount={}",
                report.getId(), suspiciousPairs.size());

        return report;
    }

    // ==================== 查询接口 ====================

    /** 查询作业的所有相似度报告 */
    public List<SimilarityReport> getReports(Long assignmentId, Long userId) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        getMemberOrThrow(assignment.getCourseId(), userId);
        return reportRepository.findByAssignmentIdOrderByGeneratedAtDesc(assignmentId);
    }

    /** 查询报告详情（含相似对列表和提交人信息） */
    public Map<String, Object> getReportDetail(Long reportId, Long userId) {
        SimilarityReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(404, "报告不存在"));
        var assignment = assignmentRepository.findById(report.getAssignmentId())
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        List<SimilarityPair> rawPairs = pairRepository
                .findByReportIdAndSimilarityScoreGreaterThanEqualOrderBySimilarityScoreDesc(
                        reportId, report.getThreshold());

        // 收集所有涉及的提交 ID 和学生 ID
        List<Long> submissionIds = new ArrayList<>();
        for (SimilarityPair p : rawPairs) {
            submissionIds.add(p.getSubmissionAId());
            submissionIds.add(p.getSubmissionBId());
        }
        submissionIds = submissionIds.stream().distinct().collect(Collectors.toList());

        // 批量加载提交和学生信息
        Map<Long, Submission> submissionMap = submissionRepository.findAllById(submissionIds)
                .stream().collect(Collectors.toMap(Submission::getId, s -> s));

        List<Long> studentIds = submissionMap.values().stream()
                .map(Submission::getStudentId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userRepository.findAllById(studentIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        // 构造富含学生信息的相似对
        List<Map<String, Object>> enrichedPairs = new ArrayList<>();
        for (SimilarityPair p : rawPairs) {
            Map<String, Object> enriched = new LinkedHashMap<>();
            enriched.put("id", p.getId());
            enriched.put("reportId", p.getReportId());
            enriched.put("submissionA", buildSubmissionSummary(p.getSubmissionAId(), submissionMap, userMap));
            enriched.put("submissionB", buildSubmissionSummary(p.getSubmissionBId(), submissionMap, userMap));
            enriched.put("similarityScore", p.getSimilarityScore());
            enriched.put("highlightedSegments", parseSegments(p.getHighlightedSegments()));
            enrichedPairs.add(enriched);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", report);
        result.put("pairs", enrichedPairs);
        return result;
    }

    // ==================== 文本提取与标准化 ====================

    /** 批量提取所有提交的文本内容 */
    private Map<Long, String> extractAllTexts(List<Submission> submissions) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (Submission sub : submissions) {
            try {
                List<SubmissionFile> files = submissionFileRepository.findBySubmissionId(sub.getId());
                String text = textExtractionService.extractText(sub, files);
                text = normalizeText(text);
                result.put(sub.getId(), text);
            } catch (Exception e) {
                log.warn("提交文本提取失败: submissionId={}", sub.getId(), e);
                // 降级：仅使用 content
                String text = normalizeText(
                        sub.getContent() != null ? sub.getContent() : "");
                result.put(sub.getId(), text);
            }
        }
        return result;
    }

    /** 文本标准化：去除多余空白、统一换行 */
    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // 统一换行为 \n
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        // 合并 3 个以上连续换行为双换行
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        // 去除每行首尾空白
        normalized = normalized.lines()
                .map(String::strip)
                .collect(Collectors.joining("\n"));
        // 截断到上限
        if (normalized.length() > MAX_TEXT_LENGTH) {
            normalized = normalized.substring(0, MAX_TEXT_LENGTH);
        }
        return normalized.strip();
    }

    // ==================== Embedding 与相似度计算 ====================

    /** 批量生成文本 Embedding */
    private List<float[]> batchEmbed(List<String> texts) {
        return embeddingModel.embed(texts);
    }

    /** 计算两个向量的余弦相似度 */
    static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致: " + a.length + " vs " + b.length);
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ==================== 相似段落定位 ====================

    /**
     * 在两份提交文本之间查找最相似的段落对。
     *
     * <p>策略：将文本按段落拆分 → 段落级 Embedding → 计算两两余弦相似度 →
     * 返回 top-{@link #MAX_HIGHLIGHTED_SEGMENTS} 最相似的段落对。
     */
    private List<Map<String, Object>> findSimilarPassages(String textA, String textB) {
        if (textA.isBlank() || textB.isBlank()) {
            return List.of();
        }

        List<String> parasA = splitParagraphs(textA);
        List<String> parasB = splitParagraphs(textB);

        // 如果段落数都很少，直接返回全文最相似部分的截取
        if (parasA.size() <= 1 && parasB.size() <= 1) {
            return List.of(Map.of(
                    "textA", truncate(textA, SEGMENT_MAX_CHARS),
                    "textB", truncate(textB, SEGMENT_MAX_CHARS),
                    "score", 1.0
            ));
        }

        // 段落级 Embedding
        List<float[]> embedA = embedParagraphs(parasA);
        List<float[]> embedB = embedParagraphs(parasB);

        // 计算所有段落对相似度
        List<SegmentPair> allPairs = new ArrayList<>();
        for (int i = 0; i < embedA.size(); i++) {
            for (int j = 0; j < embedB.size(); j++) {
                double score = cosineSimilarity(embedA.get(i), embedB.get(j));
                allPairs.add(new SegmentPair(i, j, score, parasA.get(i), parasB.get(j)));
            }
        }

        // 按相似度降序取 top-N
        return allPairs.stream()
                .sorted(Comparator.comparingDouble(SegmentPair::score).reversed())
                .limit(MAX_HIGHLIGHTED_SEGMENTS)
                .map(p -> Map.<String, Object>of(
                        "textA", truncate(p.textA(), SEGMENT_MAX_CHARS),
                        "textB", truncate(p.textB(), SEGMENT_MAX_CHARS),
                        "score", Math.round(p.score() * 10000.0) / 10000.0
                ))
                .collect(Collectors.toList());
    }

    /** 按段落拆分文本 */
    private List<String> splitParagraphs(String text) {
        String[] parts = text.split("\\n\\s*\\n");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= MIN_SEGMENT_LENGTH) {
                result.add(trimmed);
            }
        }
        // 如果按双换行拆分后无有效段落，尝试按单换行拆分
        if (result.isEmpty()) {
            for (String line : text.split("\\n")) {
                String trimmed = line.trim();
                if (trimmed.length() >= MIN_SEGMENT_LENGTH) {
                    result.add(trimmed);
                }
            }
        }
        // 保底：整段作为一个段落
        if (result.isEmpty() && !text.isBlank()) {
            result.add(text.trim());
        }
        return result;
    }

    /** 对段落列表批量生成 Embedding */
    private List<float[]> embedParagraphs(List<String> paragraphs) {
        return embeddingModel.embed(paragraphs);
    }

    /** 截断文本到指定长度 */
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    // ==================== 辅助方法 ====================

    /** 构造提交摘要（含学生姓名） */
    private Map<String, Object> buildSubmissionSummary(Long submissionId,
                                                       Map<Long, Submission> submissionMap,
                                                       Map<Long, User> userMap) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Submission sub = submissionMap.get(submissionId);
        if (sub != null) {
            summary.put("id", sub.getId());
            summary.put("studentId", sub.getStudentId());
            User user = userMap.get(sub.getStudentId());
            summary.put("studentName", user != null ?
                    (user.getRealName() != null ? user.getRealName() : user.getUsername()) : "未知");
        } else {
            summary.put("id", submissionId);
            summary.put("studentId", 0);
            summary.put("studentName", "未知");
        }
        return summary;
    }

    /** 序列化为 JSON */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "[]";
        }
    }

    /** 解析 JSON 为 List<Map> */
    private List<Map<String, Object>> parseSegments(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = objectMapper.readValue(json, List.class);
            return list;
        } catch (JsonProcessingException e) {
            log.warn("相似段落 JSON 解析失败: {}", json, e);
            return List.of();
        }
    }

    // ==================== 内部记录类 ====================

    /** 段落对（中间结果，不对外暴露） */
    private record SegmentPair(int indexA, int indexB, double score, String textA, String textB) {}
}
