package com.ketangpai.service;

import com.ketangpai.model.entity.Submission;
import com.ketangpai.model.entity.SubmissionFile;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.SubmissionStatus;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.SubmissionFileRepository;
import com.ketangpai.repository.SubmissionRepository;
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

    public SubmissionService(CourseMemberRepository courseMemberRepository,
                             SubmissionRepository submissionRepository,
                             SubmissionFileRepository submissionFileRepository) {
        super(courseMemberRepository);
        this.submissionRepository = submissionRepository;
        this.submissionFileRepository = submissionFileRepository;
    }

    @Transactional
    public Submission submit(Long assignmentId, Long studentId, String content, List<Long> fileIds) {
        // 通过作业找到课程，验证学生身份
        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
                .map(existing -> {
                    // 已有提交记录
                    if (existing.getStatus() == SubmissionStatus.GRADED
                            && !isResubmitAllowed(assignmentId)) {
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

        // 关联提交文件
        if (fileIds != null && !fileIds.isEmpty()) {
            submissionFileRepository.deleteBySubmissionId(submission.getId());
            // TODO: 关联临时文件到提交
        }

        return submission;
    }

    public List<Submission> listByAssignment(Long assignmentId, Long teacherId, String statusFilter) {
        if (statusFilter != null) {
            SubmissionStatus status = SubmissionStatus.valueOf(statusFilter);
            return submissionRepository.findByAssignmentId(assignmentId).stream()
                    .filter(s -> s.getStatus() == status)
                    .toList();
        }
        return submissionRepository.findByAssignmentId(assignmentId);
    }

    public Submission getDetail(Long submissionId, Long userId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(404, "提交不存在"));
    }

    public List<SubmissionFile> getFiles(Long submissionId) {
        return submissionFileRepository.findBySubmissionId(submissionId);
    }

    @Transactional
    public Submission grade(Long submissionId, Long teacherId, Integer score, String comment) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(404, "提交不存在"));
        checkTeacher(getAssignmentCourseId(submission.getAssignmentId()), teacherId);

        submission.setScore(score);
        submission.setTeacherComment(comment);
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(LocalDateTime.now());

        // TODO: 发送批阅完成通知给提交学生

        return submissionRepository.save(submission);
    }

    @Transactional
    public Submission returnSubmission(Long submissionId, Long teacherId, String reason) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(404, "提交不存在"));
        checkTeacher(getAssignmentCourseId(submission.getAssignmentId()), teacherId);

        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BusinessException(400, "仅已提交状态的作业可退回");
        }

        submission.setStatus(SubmissionStatus.RETURNED);
        submission.setTeacherComment(reason);
        submission = submissionRepository.save(submission);

        // TODO: 发送退回通知

        return submission;
    }

    private boolean isResubmitAllowed(Long assignmentId) {
        // TODO: 查询 assignment.allowResubmit
        return true;
    }

    private Long getAssignmentCourseId(Long assignmentId) {
        // TODO: 通过 AssignmentRepository 查询 courseId
        return 0L;
    }
}
