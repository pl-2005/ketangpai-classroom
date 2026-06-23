package com.ketangpai.service;

import com.ketangpai.model.entity.AiGradingConfig;
import com.ketangpai.model.entity.AiGradingResult;
import com.ketangpai.model.entity.Submission;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.GradingStyle;
import com.ketangpai.repository.AiGradingConfigRepository;
import com.ketangpai.repository.AiGradingResultRepository;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 智能批阅服务
 */
@Service
public class AiGradingService extends BaseService {

    private final AiGradingConfigRepository configRepository;
    private final AiGradingResultRepository resultRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;

    public AiGradingService(CourseMemberRepository courseMemberRepository,
                            AiGradingConfigRepository configRepository,
                            AiGradingResultRepository resultRepository,
                            SubmissionRepository submissionRepository,
                            AssignmentRepository assignmentRepository) {
        super(courseMemberRepository);
        this.configRepository = configRepository;
        this.resultRepository = resultRepository;
        this.submissionRepository = submissionRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public AiGradingConfig getConfig(Long assignmentId, Long userId) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        return configRepository.findByAssignmentId(assignmentId)
                .orElse(null);
    }

    @Transactional
    public AiGradingConfig updateConfig(Long assignmentId, Long userId,
                                        Boolean enabled, String promptTemplate,
                                        String rubricJson, GradingStyle gradingStyle) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        AiGradingConfig config = configRepository.findByAssignmentId(assignmentId)
                .orElseGet(() -> AiGradingConfig.builder().assignmentId(assignmentId).build());

        if (enabled != null) config.setEnabled(enabled);
        if (promptTemplate != null) config.setPromptTemplate(promptTemplate);
        if (rubricJson != null) config.setRubricJson(rubricJson);
        if (gradingStyle != null) config.setGradingStyle(gradingStyle);

        return configRepository.save(config);
    }

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

        // TODO: 调用 Spring AI ChatClient，根据 config 和 submission.content 生成评分
        //   1. 构建 Prompt（template + rubric + submission content）
        //   2. 调用 LLM，用 JSON Schema 约束输出格式
        //   3. 解析结果存入 AiGradingResult

        // 当前返回模拟结果
        AiGradingResult result = AiGradingResult.builder()
                .submissionId(submissionId)
                .score(null)
                .comment("AI 批阅功能待实现 — 需集成 Spring AI ChatClient")
                .suggestions("完成后端集成后，此服务将自动评分")
                .gradedAt(LocalDateTime.now())
                .build();

        return resultRepository.save(result);
    }

    /** 批量 AI 批阅：异步触发，返回待处理数量 */
    public long batchGrade(Long assignmentId, Long teacherId) {
        var assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), teacherId);

        AiGradingConfig config = configRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new BusinessException(400, "未配置 AI 批阅"));
        if (!config.getEnabled()) {
            throw new BusinessException(400, "AI 批阅未启用");
        }

        List<Submission> submissions = submissionRepository.findSubmittedByAssignmentId(assignmentId);
        List<Long> ungraded = submissions.stream()
                .filter(s -> resultRepository.findBySubmissionId(s.getId()).isEmpty())
                .map(Submission::getId)
                .toList();

        // TODO: 异步批量执行 AI 批阅

        return ungraded.size();
    }
}
