package com.ketangpai.service;

import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.entity.AssignmentAttachment;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.AssignmentStatus;
import com.ketangpai.repository.AssignmentAttachmentRepository;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 作业管理服务
 */
@Service
public class AssignmentService extends BaseService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentAttachmentRepository attachmentRepository;
    private final SubmissionRepository submissionRepository;

    public AssignmentService(CourseMemberRepository courseMemberRepository,
                             AssignmentRepository assignmentRepository,
                             AssignmentAttachmentRepository attachmentRepository,
                             SubmissionRepository submissionRepository) {
        super(courseMemberRepository);
        this.assignmentRepository = assignmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.submissionRepository = submissionRepository;
    }

    public List<Assignment> listByCourse(Long courseId, Long userId, String statusFilter) {
        getMemberOrThrow(courseId, userId);
        if (statusFilter != null) {
            return assignmentRepository.findByCourseIdAndStatus(courseId, AssignmentStatus.valueOf(statusFilter));
        }
        return assignmentRepository.findByCourseIdOrderByDeadlineAsc(courseId);
    }

    public Assignment getDetail(Long assignmentId, Long userId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        getMemberOrThrow(assignment.getCourseId(), userId);
        return assignment;
    }

    public List<AssignmentAttachment> getAttachments(Long assignmentId) {
        return attachmentRepository.findByAssignmentId(assignmentId);
    }

    @Transactional
    public Assignment create(Long userId, String title, String content, Long courseId,
                             java.time.LocalDateTime deadline, Integer maxScore,
                             Boolean allowResubmit, List<Long> attachmentIds) {
        checkTeacher(courseId, userId);

        Assignment assignment = Assignment.builder()
                .courseId(courseId)
                .title(title)
                .content(content)
                .status(AssignmentStatus.DRAFT)
                .deadline(deadline)
                .maxScore(maxScore != null ? maxScore : 100)
                .allowResubmit(allowResubmit != null ? allowResubmit : true)
                .build();
        assignment = assignmentRepository.save(assignment);

        // 关联附件
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            // TODO: 关联已上传的临时文件到作业
        }

        return assignment;
    }

    @Transactional
    public Assignment update(Long assignmentId, Long userId, String title, String content,
                             java.time.LocalDateTime deadline, Integer maxScore, Boolean allowResubmit) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        if (title != null) assignment.setTitle(title);
        if (content != null) assignment.setContent(content);
        if (deadline != null) assignment.setDeadline(deadline);
        if (maxScore != null) assignment.setMaxScore(maxScore);
        if (allowResubmit != null) assignment.setAllowResubmit(allowResubmit);

        return assignmentRepository.save(assignment);
    }

    @Transactional
    public Assignment updateStatus(Long assignmentId, Long userId, AssignmentStatus newStatus) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        if (newStatus == AssignmentStatus.PUBLISHED && assignment.getStatus() != AssignmentStatus.DRAFT) {
            throw new BusinessException(400, "仅草稿状态的作业可发布");
        }
        if (newStatus == AssignmentStatus.CLOSED && assignment.getStatus() != AssignmentStatus.PUBLISHED) {
            throw new BusinessException(400, "仅已发布状态的作业可关闭");
        }

        assignment.setStatus(newStatus);
        return assignmentRepository.save(assignment);
    }

    /** 催交：返回未提交学生数量 */
    public long urge(Long assignmentId, Long userId, List<Long> studentIds) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        // TODO: 发送催交通知给指定学生或所有未提交学生
        return studentIds != null ? studentIds.size()
                : submissionRepository.countByAssignmentIdAndStatus(assignmentId,
                        com.ketangpai.model.enums.SubmissionStatus.DRAFT);
    }
}
