package com.ketangpai.service;

import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.model.entity.Submission;
import com.ketangpai.model.entity.SubmissionFile;
import com.ketangpai.model.entity.TempFile;
import com.ketangpai.model.entity.User;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.AssignmentStatus;
import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.NotificationType;
import com.ketangpai.model.enums.SubmissionStatus;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.SubmissionFileRepository;
import com.ketangpai.repository.SubmissionRepository;
import com.ketangpai.repository.TempFileRepository;
import com.ketangpai.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交管理服务
 */
@Service
public class SubmissionService extends BaseService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final AssignmentRepository assignmentRepository;
    private final TempFileRepository tempFileRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final NotificationService notificationService;
    private final AiGradingService aiGradingService;

    public SubmissionService(CourseMemberRepository courseMemberRepository,
                             SubmissionRepository submissionRepository,
                             SubmissionFileRepository submissionFileRepository,
                             AssignmentRepository assignmentRepository,
                             TempFileRepository tempFileRepository,
                             UserRepository userRepository,
                             FileService fileService,
                             NotificationService notificationService,
                             AiGradingService aiGradingService) {
        super(courseMemberRepository);
        this.submissionRepository = submissionRepository;
        this.submissionFileRepository = submissionFileRepository;
        this.assignmentRepository = assignmentRepository;
        this.tempFileRepository = tempFileRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.notificationService = notificationService;
        this.aiGradingService = aiGradingService;
    }

    @Transactional
    public Submission submit(Long assignmentId, Long studentId, String content, List<Long> fileIds) {
        // 1. 校验作业存在且已发布
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        if (assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new BusinessException(400, "作业尚未发布，无法提交");
        }

        // 2. 校验学生是课程成员
        getMemberOrThrow(assignment.getCourseId(), studentId);

        // 3. 校验截止时间
        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BusinessException(400, "已超过作业截止时间");
        }

        // 4. 创建或更新提交记录
        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
                .map(existing -> {
                    // 已有提交记录
                    if (!assignment.getAllowResubmit()) {
                        throw new BusinessException(409, "该作业不允许重复提交");
                    }
                    // 版本覆盖
                    existing.setContent(content);
                    existing.setStatus(SubmissionStatus.SUBMITTED);
                    existing.setVersion(existing.getVersion() + 1);
                    existing.setSubmittedAt(LocalDateTime.now());
                    // 清除旧批阅结果
                    existing.setScore(null);
                    existing.setTeacherComment(null);
                    existing.setGradedAt(null);
                    return existing;
                })
                .orElseGet(() -> Submission.builder()
                        .assignmentId(assignmentId)
                        .studentId(studentId)
                        .content(content)
                        .status(SubmissionStatus.SUBMITTED)
                        .version(1)
                        .submittedAt(LocalDateTime.now())
                        .build());

        submission = submissionRepository.save(submission);

        // 5. 关联提交文件：从 TempFile 创建 SubmissionFile 记录
        if (fileIds != null && !fileIds.isEmpty()) {
            // 删除旧文件关联
            submissionFileRepository.deleteBySubmissionId(submission.getId());
            // 从 TempFile 创建 SubmissionFile 记录并标记关联
            for (Long fileId : fileIds) {
                TempFile tempFile = tempFileRepository.findById(fileId)
                        .orElseThrow(() -> new BusinessException(404, "文件不存在: " + fileId));
                if (Boolean.TRUE.equals(tempFile.getAssociated())) {
                    throw new BusinessException(400, "文件已被其他记录关联: " + fileId);
                }
                SubmissionFile submissionFile = SubmissionFile.builder()
                        .submissionId(submission.getId())
                        .fileName(tempFile.getFileName())
                        .fileUrl(tempFile.getFileUrl())
                        .fileSize(tempFile.getFileSize())
                        .build();
                submissionFileRepository.save(submissionFile);
                tempFile.setAssociated(true);
                tempFileRepository.save(tempFile);
            }
        }

        // 提交成功后，异步触发 AI 批阅（如果已配置且启用）
        aiGradingService.gradeSubmissionAsync(submission.getId(), submission.getAssignmentId());

        return submission;
    }

    /** 获取某作业的提交（教师返回全部，学生返回自己的提交） */
    public List<Submission> listByAssignment(Long assignmentId, Long userId, String statusFilter) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));

        // 检查用户是教师还是学生
        CourseMember member = getMemberOrThrow(assignment.getCourseId(), userId);
        boolean isStudent = member.getRole() == CourseMemberRole.STUDENT;

        List<Submission> submissions;

        if (isStudent) {
            // 学生仅返回自己的提交
            submissions = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, userId)
                    .stream().toList();
        } else if (statusFilter != null) {
            // 教师（含创建者）按状态筛选返回
            SubmissionStatus status = SubmissionStatus.valueOf(statusFilter);
            submissions = submissionRepository.findByAssignmentId(assignmentId).stream()
                    .filter(s -> s.getStatus() == status)
                    .toList();
        } else {
            submissions = submissionRepository.findByAssignmentId(assignmentId);
        }

        // 填充学生姓名和用户名
        populateStudentInfo(submissions);
        return submissions;
    }

    /** 从 User 表批量填充 studentName / studentUsername */
    private void populateStudentInfo(List<Submission> submissions) {
        if (submissions.isEmpty()) return;
        var userIds = submissions.stream().map(Submission::getStudentId).distinct().toList();
        var userMap = userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        User::getId,
                        u -> u));
        for (Submission s : submissions) {
            var u = userMap.get(s.getStudentId());
            if (u != null) {
                s.setStudentName(u.getRealName());
                s.setStudentUsername(u.getUsername());
            }
        }
    }

    /** 获取单个提交详情（教师或提交者本人可查看） */
    public Submission getDetail(Long submissionId, Long userId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(404, "提交不存在"));

        // 填充学生信息
        populateStudentInfo(List.of(submission));

        // 提交者本人可查看自己的提交
        if (submission.getStudentId().equals(userId)) {
            return submission;
        }

        // 教师可查看课程内任意提交
        Assignment assignment = assignmentRepository.findById(submission.getAssignmentId())
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        return submission;
    }

    public List<SubmissionFile> getFiles(Long submissionId) {
        return submissionFileRepository.findBySubmissionId(submissionId);
    }

    @Transactional
    public Submission grade(Long submissionId, Long teacherId, Integer score, String comment) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(404, "提交不存在"));

        Long courseId = getAssignmentCourseId(submission.getAssignmentId());
        checkTeacher(courseId, teacherId);

        submission.setScore(score);
        submission.setTeacherComment(comment);
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(LocalDateTime.now());
        submission = submissionRepository.save(submission);

        // 发送批阅完成通知给提交学生
        Assignment assignment = assignmentRepository.findById(submission.getAssignmentId()).orElse(null);
        String assignmentTitle = assignment != null ? assignment.getTitle() : "未知作业";
        notificationService.create(
                submission.getStudentId(),
                courseId,
                NotificationType.ASSIGNMENT_GRADED,
                "作业已批阅",
                "作业「" + assignmentTitle + "」已批阅，得分：" + (score != null ? score : "未评分"),
                submission.getId()
        );

        return submission;
    }

    @Transactional
    public Submission returnSubmission(Long submissionId, Long teacherId, String reason) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(404, "提交不存在"));

        Long courseId = getAssignmentCourseId(submission.getAssignmentId());
        checkTeacher(courseId, teacherId);

        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BusinessException(400, "仅已提交状态的作业可退回");
        }

        submission.setStatus(SubmissionStatus.RETURNED);
        submission.setTeacherComment(reason);
        submission = submissionRepository.save(submission);

        // 发送退回通知给提交学生
        Assignment assignment = assignmentRepository.findById(submission.getAssignmentId()).orElse(null);
        String assignmentTitle = assignment != null ? assignment.getTitle() : "未知作业";
        notificationService.create(
                submission.getStudentId(),
                courseId,
                NotificationType.ASSIGNMENT_RETURNED,
                "作业被退回",
                "作业「" + assignmentTitle + "」已被退回" +
                        (reason != null && !reason.isEmpty() ? "，原因：" + reason : ""),
                submission.getId()
        );

        return submission;
    }

    /** 通过作业 ID 查询所属课程 ID */
    private Long getAssignmentCourseId(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .map(Assignment::getCourseId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
    }
}
