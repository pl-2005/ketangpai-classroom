package com.ketangpai.service;

import com.ketangpai.dto.assignment.AssignmentListResponse;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.model.entity.Submission;
import com.ketangpai.model.enums.AssignmentStatus;
import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.SubmissionStatus;
import com.ketangpai.repository.AssignmentAttachmentRepository;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentAttachmentRepository attachmentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Test
    void studentOnlyReceivesPublishedLifecycleAssignmentsAndOwnSubmissionStatus() {
        AssignmentService service = service();
        PageRequest pageable = PageRequest.of(0, 20);
        Assignment assignment = assignment(30L, AssignmentStatus.PUBLISHED);
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(member(CourseMemberRole.STUDENT)));
        when(assignmentRepository.findVisiblePageByCourseId(
                10L, List.of(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED), pageable))
                .thenReturn(new PageImpl<>(List.of(assignment), pageable, 1));
        Submission submission = Submission.builder()
                .assignmentId(30L)
                .studentId(2L)
                        .status(SubmissionStatus.SUBMITTED)
                .build();
        when(submissionRepository.findByStudentIdAndAssignmentIdIn(2L, List.of(30L)))
                .thenReturn(List.of(submission));

        Page<AssignmentListResponse> result = service.listByCourse(10L, 2L, null, pageable);

        assertThat(result.getContent())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(30L);
                    assertThat(item.mySubmissionStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
                });
        verify(submissionRepository).findByStudentIdAndAssignmentIdIn(2L, List.of(30L));
    }

    @Test
    void studentAssignmentPageLoadsSubmissionStatusesInOneBatch() {
        AssignmentService service = service();
        PageRequest pageable = PageRequest.of(0, 20);
        Assignment first = assignment(30L, AssignmentStatus.PUBLISHED);
        Assignment second = assignment(31L, AssignmentStatus.CLOSED);
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(member(CourseMemberRole.STUDENT)));
        when(assignmentRepository.findVisiblePageByCourseId(
                10L, List.of(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED), pageable))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(submissionRepository.findByStudentIdAndAssignmentIdIn(2L, List.of(30L, 31L)))
                .thenReturn(List.of(Submission.builder()
                        .assignmentId(31L)
                        .studentId(2L)
                        .status(SubmissionStatus.GRADED)
                        .build()));

        Page<AssignmentListResponse> result = service.listByCourse(10L, 2L, null, pageable);

        assertThat(result.getContent())
                .extracting(AssignmentListResponse::mySubmissionStatus)
                .containsExactly(null, SubmissionStatus.GRADED);
        verify(submissionRepository).findByStudentIdAndAssignmentIdIn(
                2L, List.of(30L, 31L));
    }

    @Test
    void studentCannotFilterForDraftAssignments() {
        AssignmentService service = service();
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(member(CourseMemberRole.STUDENT)));

        assertThatThrownBy(() -> service.listByCourse(
                10L, 2L, "draft", PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
    }

    @Test
    void teacherCanFilterAllAssignmentStates() {
        AssignmentService service = service();
        PageRequest pageable = PageRequest.of(0, 20);
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(member(CourseMemberRole.TEACHER)));
        when(assignmentRepository.findPageByCourseId(10L, AssignmentStatus.DRAFT, pageable))
                .thenReturn(Page.empty(pageable));

        service.listByCourse(10L, 1L, "DRAFT", pageable);

        verify(assignmentRepository).findPageByCourseId(
                10L, AssignmentStatus.DRAFT, pageable);
    }

    @Test
    void invalidStatusReturnsBadRequest() {
        AssignmentService service = service();
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(member(CourseMemberRole.TEACHER)));

        assertThatThrownBy(() -> service.listByCourse(
                10L, 1L, "UNKNOWN", PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    private AssignmentService service() {
        return new AssignmentService(
                courseMemberRepository,
                assignmentRepository,
                attachmentRepository,
                submissionRepository);
    }

    private CourseMember member(CourseMemberRole role) {
        return CourseMember.builder()
                .courseId(10L)
                .userId(1L)
                .role(role)
                .deleted(false)
                .build();
    }

    private Assignment assignment(Long id, AssignmentStatus status) {
        Assignment assignment = Assignment.builder()
                .courseId(10L)
                .title("需求分析作业")
                .status(status)
                .build();
        assignment.setId(id);
        return assignment;
    }
}
