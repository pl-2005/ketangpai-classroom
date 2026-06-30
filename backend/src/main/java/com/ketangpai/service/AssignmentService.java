package com.ketangpai.service;

import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.entity.AssignmentAttachment;
import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.model.entity.Submission;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.AssignmentStatus;
import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.NotificationType;
import com.ketangpai.model.enums.SubmissionStatus;
import com.ketangpai.repository.AssignmentAttachmentRepository;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 作业管理服务
 */
@Service
public class AssignmentService extends BaseService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentAttachmentRepository attachmentRepository;
    private final SubmissionRepository submissionRepository;
    private final NotificationService notificationService;
    private final KnowledgeBaseService knowledgeBaseService;

    public AssignmentService(CourseMemberRepository courseMemberRepository,
                             AssignmentRepository assignmentRepository,
                             AssignmentAttachmentRepository attachmentRepository,
                             SubmissionRepository submissionRepository,
                             NotificationService notificationService,
                             KnowledgeBaseService knowledgeBaseService) {
        super(courseMemberRepository);
        this.assignmentRepository = assignmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.submissionRepository = submissionRepository;
        this.notificationService = notificationService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public List<Assignment> listByCourse(Long courseId, Long userId, String statusFilter) {
        getMemberOrThrow(courseId, userId);
        boolean isTeacher = isTeacher(courseId, userId);

        List<Assignment> assignments;

        if (statusFilter != null) {
            AssignmentStatus requestedStatus = AssignmentStatus.valueOf(statusFilter);
            // 学生不能查看草稿状态作业
            if (!isTeacher && requestedStatus == AssignmentStatus.DRAFT) {
                return List.of();
            }
            assignments = assignmentRepository.findByCourseIdAndStatusOrderByCreateTimeDesc(courseId, requestedStatus);
        } else if (isTeacher) {
            // 教师可查看所有作业，学生只能看到已发布和已关闭的作业
            assignments = assignmentRepository.findByCourseIdOrderByCreateTimeDesc(courseId);
        } else {
            assignments = assignmentRepository.findByCourseIdAndStatusInOrderByCreateTimeDesc(
                    courseId, List.of(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED));
        }

        if (assignments.isEmpty()) return assignments;

        // 批量获取所有相关提交记录
        List<Long> assignmentIds = assignments.stream().map(Assignment::getId).toList();
        List<Submission> allSubmissions = submissionRepository.findByAssignmentIdIn(assignmentIds);

        if (isTeacher) {
            // 教师端：填充每个作业的提交统计
            long totalStudents = courseMemberRepository.countStudentsByCourseId(courseId);
            Map<Long, List<Submission>> grouped = allSubmissions.stream()
                    .collect(Collectors.groupingBy(Submission::getAssignmentId));
            for (Assignment a : assignments) {
                List<Submission> subs = grouped.getOrDefault(a.getId(), List.of());
                long submittedCount = subs.stream()
                        .filter(s -> s.getStatus() == SubmissionStatus.SUBMITTED
                                || s.getStatus() == SubmissionStatus.GRADED
                                || s.getStatus() == SubmissionStatus.RETURNED)
                        .count();
                long gradedCount = subs.stream()
                        .filter(s -> s.getStatus() == SubmissionStatus.GRADED)
                        .count();
                a.setStats(Map.of(
                        "totalStudents", totalStudents,
                        "submittedCount", submittedCount,
                        "gradedCount", gradedCount
                ));
            }
        } else {
            // 学生端：填充当前用户对每个作业的提交状态
            Map<Long, Submission> mySubmissions = allSubmissions.stream()
                    .filter(s -> s.getStudentId().equals(userId))
                    .collect(Collectors.toMap(Submission::getAssignmentId, s -> s, (s1, s2) -> s1));
            for (Assignment a : assignments) {
                Submission mine = mySubmissions.get(a.getId());
                a.setMySubmissionStatus(mine != null ? mine.getStatus().name() : null);
            }
        }

        return assignments;
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

        // 异步索引到知识库
        knowledgeBaseService.indexAssignment(assignment);

        return assignment;
    }

    @Transactional
    public Assignment update(Long assignmentId, Long userId, String title, String content,
                             java.time.LocalDateTime deadline, Integer maxScore, Boolean allowResubmit) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        // 已发布作业只能修改 deadline（延期）和 content（修正要求）
        if (assignment.getStatus() == AssignmentStatus.PUBLISHED) {
            if (deadline != null) assignment.setDeadline(deadline);
            if (content != null) assignment.setContent(content);
            return assignmentRepository.save(assignment);
        }

        if (title != null) assignment.setTitle(title);
        if (content != null) assignment.setContent(content);
        if (deadline != null) assignment.setDeadline(deadline);
        if (maxScore != null) assignment.setMaxScore(maxScore);
        if (allowResubmit != null) assignment.setAllowResubmit(allowResubmit);

        assignment = assignmentRepository.save(assignment);

        // 内容变更后重新索引
        if (content != null || title != null) {
            knowledgeBaseService.indexAssignment(assignment);
        }

        return assignment;
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
        assignment = assignmentRepository.save(assignment);

        // 发布时通知课程全体学生 + 索引到知识库
        if (newStatus == AssignmentStatus.PUBLISHED) {
            knowledgeBaseService.indexAssignment(assignment);
            List<CourseMember> students = courseMemberRepository
                    .findByCourseIdAndRole(assignment.getCourseId(), CourseMemberRole.STUDENT);
            if (!students.isEmpty()) {
                notificationService.createBatch(
                        students.stream().map(CourseMember::getUserId).toList(),
                        assignment.getCourseId(),
                        NotificationType.ASSIGNMENT_PUBLISHED,
                        "新作业发布",
                        "作业「" + assignment.getTitle() + "」已发布，截止时间：" +
                                (assignment.getDeadline() != null ? assignment.getDeadline().toString() : "暂未设置"),
                        assignment.getId()
                );
            }
        }

        return assignment;
    }

    /** 催交：向未提交学生发送通知，返回通知数量 */
    public long urge(Long assignmentId, Long userId, List<Long> studentIds) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(404, "作业不存在"));
        checkTeacher(assignment.getCourseId(), userId);

        // 如果未指定学生，则向所有未提交的学生发送催交通知
        List<Long> targetStudentIds;
        if (studentIds != null && !studentIds.isEmpty()) {
            targetStudentIds = studentIds;
        } else {
            // 查课程所有学生
            List<CourseMember> students = courseMemberRepository
                    .findByCourseIdAndRole(assignment.getCourseId(), CourseMemberRole.STUDENT);
            // 查已提交的学生 ID
            List<Long> submittedStudentIds = submissionRepository
                    .findSubmittedByAssignmentId(assignmentId).stream()
                    .map(s -> s.getStudentId())
                    .toList();
            targetStudentIds = students.stream()
                    .map(CourseMember::getUserId)
                    .filter(id -> !submittedStudentIds.contains(id))
                    .toList();
        }

        if (!targetStudentIds.isEmpty()) {
            notificationService.createBatch(
                    targetStudentIds,
                    assignment.getCourseId(),
                    NotificationType.ASSIGNMENT_URGED,
                    "作业催交",
                    "请尽快提交作业「" + assignment.getTitle() + "」",
                    assignment.getId()
            );
        }

        return targetStudentIds.size();
    }

    /** 检查用户是否为课程教师（不抛异常，返回 boolean） */
    private boolean isTeacher(Long courseId, Long userId) {
        try {
            checkTeacher(courseId, userId);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }
}
