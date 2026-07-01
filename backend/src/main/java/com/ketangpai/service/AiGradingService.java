package com.ketangpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ketangpai.dto.ai.AiGradingResponse;
import com.ketangpai.dto.ai.RubricValidationResult;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.entity.AiGradingConfig;
import com.ketangpai.model.entity.AiGradingResult;
import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.entity.GradingBatchTask;
import com.ketangpai.model.entity.Submission;
import com.ketangpai.model.entity.SubmissionFile;
import com.ketangpai.model.enums.GradingBatchTaskStatus;
import com.ketangpai.model.enums.GradingStyle;
import com.ketangpai.model.enums.NotificationType;
import com.ketangpai.repository.AiGradingConfigRepository;
import com.ketangpai.repository.AiGradingResultRepository;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.GradingBatchTaskRepository;
import com.ketangpai.repository.SubmissionFileRepository;
import com.ketangpai.repository.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 智能批阅服务
 * <p>
 * 核心流程：
 * <ol>
 *   <li>教师配置评分标准（Rubric + Prompt 模板 + 批阅风格）</li>
 *   <li>学生提交 → 自动异步触发 AI 批阅（若已启用）</li>
 *   <li>教师可手动重新触发单份批阅（同步返回结果）</li>
 *   <li>教师可批量批阅全部未评提交（异步，可查询进度）</li>
 * </ol>
 */
@Slf4j
@Service
public class AiGradingService extends BaseService {

    private static final int MAX_SUBMISSION_TEXT_LENGTH = 30_000;

    private final AiGradingConfigRepository configRepository;
    private final AiGradingResultRepository resultRepository;
    private final GradingBatchTaskRepository batchTaskRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final AssignmentRepository assignmentRepository;
    private final ChatClient aiGradingChatClient;
    private final TextExtractionService textExtractionService;
    private final RubricValidator rubricValidator;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AiGradingService(CourseMemberRepository courseMemberRepository,
                            AiGradingConfigRepository configRepository,
                            AiGradingResultRepository resultRepository,
                            GradingBatchTaskRepository batchTaskRepository,
                            SubmissionRepository submissionRepository,
                            SubmissionFileRepository submissionFileRepository,
                            AssignmentRepository assignmentRepository,
                            ChatClient aiGradingChatClient,
                            TextExtractionService textExtractionService,
                            RubricValidator rubricValidator,
                            NotificationService notificationService,
                            ObjectMapper objectMapper,
                            ApplicationEventPublisher eventPublisher) {
        super(courseMemberRepository);
        this.configRepository = configRepository;
        this.resultRepository = resultRepository;
        this.batchTaskRepository = batchTaskRepository;
        this.submissionRepository = submissionRepository;
        this.submissionFileRepository = submissionFileRepository;
        this.assignmentRepository = assignmentRepository;
        this.aiGradingChatClient = aiGradingChatClient;
        this.textExtractionService = textExtractionService;
        this.rubricValidator = rubricValidator;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 配置管理 ====================

    public AiGradingConfig getConfig(Long assignmentId, Long userId) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);
        return configRepository.findByAssignmentId(assignmentId).orElse(null);
    }

    @Transactional
    public AiGradingConfig updateConfig(Long assignmentId, Long userId,
                                        Boolean enabled, String promptTemplate,
                                        String rubricJson, GradingStyle gradingStyle) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        // 校验 rubricJson 合法性
        if (rubricJson != null) {
            RubricValidationResult validation = rubricValidator.validate(
                    rubricJson, promptTemplate, assignment.getMaxScore());
            if (!validation.valid()) {
                throw new BusinessException(400,
                        "评分标准校验失败: " + String.join("; ", validation.errors()));
            }
        }

        // 如果只更新了 promptTemplate 但 rubric 未变，也校验占位符
        if (promptTemplate != null && rubricJson == null) {
            AiGradingConfig existing = configRepository.findByAssignmentId(assignmentId).orElse(null);
            String existingRubric = existing != null ? existing.getRubricJson() : "[]";
            RubricValidationResult validation = rubricValidator.validate(
                    existingRubric, promptTemplate, assignment.getMaxScore());
            if (!validation.valid()) {
                throw new BusinessException(400,
                        "Prompt 模板校验失败: " + String.join("; ", validation.errors()));
            }
        }

        AiGradingConfig config = configRepository.findByAssignmentId(assignmentId)
                .orElseGet(() -> AiGradingConfig.builder().assignmentId(assignmentId).build());

        if (enabled != null) config.setEnabled(enabled);
        if (promptTemplate != null) config.setPromptTemplate(promptTemplate);
        if (rubricJson != null) config.setRubricJson(rubricJson);
        if (gradingStyle != null) config.setGradingStyle(gradingStyle);
        config.setUpdateTime(LocalDateTime.now());

        return configRepository.save(config);
    }

    // ==================== 同步单份批阅（教师手动触发） ====================

    /**
     * 教师手动触发单份提交的 AI 批阅 — 同步执行，直接返回结果。
     */
    @Transactional
    public AiGradingResult gradeSubmission(Long submissionId, Long teacherId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(404, "提交不存在"));
        var assignment = assignmentRepository.findById(submission.getAssignmentId())
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), teacherId);

        AiGradingConfig config = configRepository.findByAssignmentId(submission.getAssignmentId())
                .orElseThrow(() -> new BusinessException(400, "未配置 AI 批阅"));
        if (!config.getEnabled()) {
            throw new BusinessException(400, "AI 批阅未启用");
        }

        return doGrade(submission, assignment, config);
    }

    // ==================== 异步自动批阅（学生提交后触发） ====================

    /**
     * 学生提交后异步触发 AI 批阅 — 检查配置后异步执行，不阻塞提交响应。
     */
    @Async
    public void gradeSubmissionAsync(Long submissionId, Long assignmentId) {
        try {
            AiGradingConfig config = configRepository.findByAssignmentId(assignmentId).orElse(null);
            if (config == null || !config.getEnabled()) {
                log.debug("作业 {} 未启用 AI 批阅，跳过", assignmentId);
                return;
            }

            Submission submission = submissionRepository.findById(submissionId).orElse(null);
            if (submission == null) {
                log.warn("异步批阅：提交 {} 不存在", submissionId);
                return;
            }

            Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
            if (assignment == null) {
                log.warn("异步批阅：作业 {} 不存在", assignmentId);
                return;
            }

            doGrade(submission, assignment, config);
            log.info("异步 AI 批阅完成: submissionId={}", submissionId);
        } catch (Exception e) {
            log.error("异步 AI 批阅失败: submissionId={}, assignmentId={}", submissionId, assignmentId, e);
        }
    }

    // ==================== 批量批阅 ====================

    /**
     * 批量触发 AI 批阅 — 创建任务记录后异步执行，返回任务信息供前端轮询进度。
     */
    @Transactional
    public GradingBatchTask batchGrade(Long assignmentId, Long teacherId) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), teacherId);

        AiGradingConfig config = configRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new BusinessException(400, "未配置 AI 批阅"));
        if (!config.getEnabled()) {
            throw new BusinessException(400, "AI 批阅未启用");
        }

        // 收集待批阅的提交 ID
        List<Submission> submissions = submissionRepository.findSubmittedByAssignmentId(assignmentId);
        List<Long> ungradedIds = submissions.stream()
                .filter(s -> resultRepository.findBySubmissionId(s.getId()).isEmpty())
                .map(Submission::getId)
                .toList();

        if (ungradedIds.isEmpty()) {
            throw new BusinessException(400, "没有待批阅的提交");
        }

        // 创建任务记录
        GradingBatchTask task = GradingBatchTask.builder()
                .assignmentId(assignmentId)
                .teacherId(teacherId)
                .status(GradingBatchTaskStatus.PENDING)
                .totalCount(ungradedIds.size())
                .build();
        task = batchTaskRepository.save(task);

        // 事务提交后异步执行批量批阅，启动接口立即返回任务信息
        eventPublisher.publishEvent(new GradingBatchTaskRequestedEvent(task.getId(), ungradedIds));

        return task;
    }

    /**
     * 异步批量处理 — 逐份批阅，每份完成后更新进度。
     */
    public void processBatch(Long taskId, List<Long> submissionIds) {
        GradingBatchTask task = batchTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.error("批量任务不存在: taskId={}", taskId);
            return;
        }

        task.setStatus(GradingBatchTaskStatus.IN_PROGRESS);
        task.setUpdateTime(LocalDateTime.now());
        batchTaskRepository.save(task);

        Assignment assignment = assignmentRepository.findById(task.getAssignmentId()).orElse(null);
        AiGradingConfig config = configRepository.findByAssignmentId(task.getAssignmentId()).orElse(null);
        if (assignment == null || config == null) {
            task.setStatus(GradingBatchTaskStatus.FAILED);
            task.setErrorMessage("作业或配置不存在");
            task.setUpdateTime(LocalDateTime.now());
            batchTaskRepository.save(task);
            return;
        }

        for (Long submissionId : submissionIds) {
            try {
                Submission submission = submissionRepository.findById(submissionId).orElse(null);
                if (submission == null) {
                    task.setFailedCount(task.getFailedCount() + 1);
                    continue;
                }
                doGrade(submission, assignment, config);
                task.setCompletedCount(task.getCompletedCount() + 1);
            } catch (Exception e) {
                log.error("批量批阅中单份失败: submissionId={}", submissionId, e);
                task.setFailedCount(task.getFailedCount() + 1);
                // 保存错误但不清除已有结果，继续处理下一份
                saveFailedResult(submissionId, e.getMessage());
            }
            task.setUpdateTime(LocalDateTime.now());
            batchTaskRepository.save(task);
        }

        // 更新最终状态
        if (task.getFailedCount() == 0) {
            task.setStatus(GradingBatchTaskStatus.COMPLETED);
        } else if (task.getCompletedCount() > 0) {
            task.setStatus(GradingBatchTaskStatus.PARTIALLY_FAILED);
        } else {
            task.setStatus(GradingBatchTaskStatus.FAILED);
            task.setErrorMessage("全部批阅失败，请检查 AI 服务是否正常");
        }
        task.setUpdateTime(LocalDateTime.now());
        batchTaskRepository.save(task);

        log.info("批量批阅完成: taskId={}, completed={}, failed={}",
                taskId, task.getCompletedCount(), task.getFailedCount());
    }

    // ==================== 任务状态查询 ====================

    /** 查询某作业的批量任务列表（按创建时间倒序） */
    public List<GradingBatchTask> getBatchTasks(Long assignmentId, Long userId) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);
        return batchTaskRepository.findByAssignmentIdOrderByCreateTimeDesc(assignmentId);
    }

    /** 查询单个批量任务详情 */
    public GradingBatchTask getTaskDetail(Long taskId, Long userId) {
        GradingBatchTask task = batchTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));
        var assignment = assignmentRepository.findById(task.getAssignmentId())
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);
        return task;
    }

    // ==================== 核心批阅逻辑 ====================

    /**
     * 实际执行 AI 批阅，保存结果并发送通知。
     * 调用方需确保权限检查已完成、config 已启用。
     */
    private AiGradingResult doGrade(Submission submission, Assignment assignment, AiGradingConfig config) {
        try {
            // 1. 提取提交文本（content + 附件文件内容）
            List<SubmissionFile> files = submissionFileRepository.findBySubmissionId(submission.getId());
            String fullText = textExtractionService.extractText(submission, files);

            if (fullText.isBlank()) {
                return saveEmptyResult(submission.getId(), "无可提取的文本内容");
            }

            // 2. 构建 Prompt
            String rubricText = formatRubricForPrompt(config.getRubricJson());
            String styleInstruction = getGradingStyleInstruction(config.getGradingStyle());
            String systemPrompt = buildSystemPrompt(rubricText, assignment.getMaxScore(), styleInstruction);
            String userPrompt = buildUserPrompt(fullText);

            log.info("AI 批阅开始: submissionId={}, textLen={}", submission.getId(), fullText.length());

            // 3. 调用 LLM（结构化输出）
            AiGradingResponse response = aiGradingChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(AiGradingResponse.class);

            if (response == null) {
                throw new BusinessException(500, "AI 未返回有效评分结果");
            }

            // 4. 校验并保存结果
            int score = clampScore(response.score(), assignment.getMaxScore());
            String detailJson = objectMapper.writeValueAsString(response.dimensions());

            // 若已有旧结果，删除（支持重新批阅）
            resultRepository.findBySubmissionId(submission.getId())
                    .ifPresent(old -> resultRepository.delete(old));

            AiGradingResult result = AiGradingResult.builder()
                    .submissionId(submission.getId())
                    .score(score)
                    .comment(response.comment())
                    .suggestions(response.suggestions())
                    .detailJson(detailJson)
                    .gradedAt(LocalDateTime.now())
                    .build();
            result = resultRepository.save(result);

            // 5. 发送通知给提交学生
            notificationService.create(
                    submission.getStudentId(),
                    assignment.getCourseId(),
                    NotificationType.AI_GRADED,
                    "AI 批阅完成",
                    "作业「" + assignment.getTitle() + "」AI 批阅已完成，AI 预评分：" + score + "，请查看结果",
                    submission.getId()
            );

            log.info("AI 批阅完成: submissionId={}, score={}", submission.getId(), score);
            return result;

        } catch (Exception e) {
            log.error("AI 批阅失败: submissionId={}", submission.getId(), e);
            return saveFailedResult(submission.getId(),
                    "AI 批阅失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /** 保存失败的批阅结果（不中断批量、供查询） */
    private AiGradingResult saveFailedResult(Long submissionId, String errorMessage) {
        resultRepository.findBySubmissionId(submissionId).ifPresent(old -> resultRepository.delete(old));
        return resultRepository.save(AiGradingResult.builder()
                .submissionId(submissionId)
                .score(null)
                .comment("AI 批阅失败")
                .suggestions(errorMessage)
                .gradedAt(LocalDateTime.now())
                .build());
    }

    /** 保存空内容结果 */
    private AiGradingResult saveEmptyResult(Long submissionId, String message) {
        resultRepository.findBySubmissionId(submissionId).ifPresent(old -> resultRepository.delete(old));
        return resultRepository.save(AiGradingResult.builder()
                .submissionId(submissionId)
                .score(null)
                .comment(message)
                .suggestions("请检查提交是否包含可读取的文本内容")
                .gradedAt(LocalDateTime.now())
                .build());
    }

    /** 将评分限制在 [0, maxScore] 范围内 */
    private int clampScore(int score, int maxScore) {
        return Math.max(0, Math.min(score, maxScore));
    }

    /** 将 rubricJson 格式化为 LLM 可读的文本 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String formatRubricForPrompt(String rubricJson) {
        try {
            List<Map> items = objectMapper.readValue(rubricJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            StringBuilder sb = new StringBuilder();
            int idx = 1;
            for (Map map : items) {
                sb.append(String.format("%d. %s（权重 %d%%, 满分 %d 分）\n   标准：%s\n",
                        idx,
                        map.get("dimension"),
                        toInt(map.get("weight")),
                        toInt(map.get("maxScore")),
                        map.get("criteria")));
                idx++;
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("rubricJson 格式化失败，使用原始值", e);
            return rubricJson;
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /** 根据批阅风格生成教学指令 */
    private String getGradingStyleInstruction(GradingStyle style) {
        return switch (style) {
            case STRICT -> "从严评分，扣分严格，评语直接指出全部问题，不能遗漏";
            case BALANCED -> "客观公正，既肯定优点也指出不足，给出建设性意见";
            case ENCOURAGING -> "鼓励为主，适当放宽扣分标准，多给正面反馈和鼓励";
            case CONCISE -> "简洁高效，评分准确但评语言简意赅，控制在200字以内";
        };
    }

    /** 构建 System Prompt */
    private String buildSystemPrompt(String rubricText, int maxScore, String styleInstruction) {
        return """
                你是一位严谨而公正的课程助教，正在批阅学生作业。
                作业满分：%d 分。

                ## 评分标准（Rubric）
                %s

                ## 评分要求
                1. 严格按照评分标准的各维度逐项评分，给出各维度的得分和评语。
                2. 计算加权总分，最终分数为整数（0-%d 分）。
                3. 写一段总体评语（comment），指出优点和不足。
                4. 给出具体的改进建议（suggestions），帮助学生提高。
                5. 评分风格：%s

                请以严格的 JSON 格式输出，不要输出其他内容。格式如下：
                {
                  "score": <整数>,
                  "comment": "<总体评语>",
                  "suggestions": "<改进建议>",
                  "dimensions": [
                    {
                      "dimension": "<维度名>",
                      "score": <整数>,
                      "maxScore": <整数>,
                      "comment": "<该维度评语>"
                    }
                  ]
                }
                """.formatted(maxScore, rubricText, maxScore, styleInstruction);
    }

    /** 构建 User Prompt */
    private String buildUserPrompt(String submissionText) {
        String text = submissionText;
        if (text.length() > MAX_SUBMISSION_TEXT_LENGTH) {
            text = text.substring(0, MAX_SUBMISSION_TEXT_LENGTH)
                    + "\n\n...[内容已截断，仅显示前" + MAX_SUBMISSION_TEXT_LENGTH + "字符]";
        }
        return "## 学生提交内容\n\n" + text;
    }
}
