package com.ketangpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ketangpai.model.entity.AiGradingConfig;
import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.model.entity.GradingBatchTask;
import com.ketangpai.model.entity.Submission;
import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.GradingBatchTaskStatus;
import com.ketangpai.repository.AiGradingConfigRepository;
import com.ketangpai.repository.AiGradingResultRepository;
import com.ketangpai.repository.AssignmentRepository;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.GradingBatchTaskRepository;
import com.ketangpai.repository.SubmissionFileRepository;
import com.ketangpai.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGradingServiceTest {

    @Test
    void batchGradeReturnsPendingTaskWithoutProcessingSynchronously() {
        CourseMemberRepository courseMemberRepository = mock(CourseMemberRepository.class);
        AiGradingConfigRepository configRepository = mock(AiGradingConfigRepository.class);
        AiGradingResultRepository resultRepository = mock(AiGradingResultRepository.class);
        GradingBatchTaskRepository batchTaskRepository = mock(GradingBatchTaskRepository.class);
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionFileRepository submissionFileRepository = mock(SubmissionFileRepository.class);
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        ChatClient aiGradingChatClient = mock(ChatClient.class);
        TextExtractionService textExtractionService = mock(TextExtractionService.class);
        RubricValidator rubricValidator = mock(RubricValidator.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        AiGradingService service = new AiGradingService(
                courseMemberRepository,
                configRepository,
                resultRepository,
                batchTaskRepository,
                submissionRepository,
                submissionFileRepository,
                assignmentRepository,
                aiGradingChatClient,
                textExtractionService,
                rubricValidator,
                notificationService,
                new ObjectMapper(),
                eventPublisher
        );

        Assignment assignment = Assignment.builder()
                .courseId(3L)
                .title("期末作业")
                .build();
        AiGradingConfig config = AiGradingConfig.builder()
                .assignmentId(9L)
                .enabled(true)
                .rubricJson("[]")
                .build();
        Submission submission = Submission.builder()
                .id(11L)
                .assignmentId(9L)
                .studentId(21L)
                .content("提交内容")
                .build();
        GradingBatchTask savedTask = GradingBatchTask.builder()
                .assignmentId(9L)
                .teacherId(5L)
                .status(GradingBatchTaskStatus.PENDING)
                .totalCount(1)
                .build();
        savedTask.setId(123L);

        when(assignmentRepository.findById(9L)).thenReturn(Optional.of(assignment));
        when(courseMemberRepository.findByCourseIdAndUserId(3L, 5L))
                .thenReturn(Optional.of(CourseMember.builder()
                        .courseId(3L)
                        .userId(5L)
                        .role(CourseMemberRole.TEACHER)
                        .build()));
        when(configRepository.findByAssignmentId(9L)).thenReturn(Optional.of(config));
        when(submissionRepository.findSubmittedByAssignmentId(9L)).thenReturn(List.of(submission));
        when(resultRepository.findBySubmissionId(11L)).thenReturn(Optional.empty());
        when(batchTaskRepository.save(any(GradingBatchTask.class))).thenReturn(savedTask);

        GradingBatchTask result = service.batchGrade(9L, 5L);

        assertEquals(123L, result.getId());
        assertEquals(GradingBatchTaskStatus.PENDING, result.getStatus());
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event ->
                event instanceof GradingBatchTaskRequestedEvent requested
                        && requested.taskId().equals(123L)
                        && requested.submissionIds().equals(List.of(11L))));
        verify(batchTaskRepository, never()).findById(123L);
    }

    @Test
    void gradeSubmissionAsyncTaskReturnsPendingTaskWithoutProcessingSynchronously() {
        CourseMemberRepository courseMemberRepository = mock(CourseMemberRepository.class);
        AiGradingConfigRepository configRepository = mock(AiGradingConfigRepository.class);
        AiGradingResultRepository resultRepository = mock(AiGradingResultRepository.class);
        GradingBatchTaskRepository batchTaskRepository = mock(GradingBatchTaskRepository.class);
        SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        SubmissionFileRepository submissionFileRepository = mock(SubmissionFileRepository.class);
        AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
        ChatClient aiGradingChatClient = mock(ChatClient.class);
        TextExtractionService textExtractionService = mock(TextExtractionService.class);
        RubricValidator rubricValidator = mock(RubricValidator.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        AiGradingService service = new AiGradingService(
                courseMemberRepository,
                configRepository,
                resultRepository,
                batchTaskRepository,
                submissionRepository,
                submissionFileRepository,
                assignmentRepository,
                aiGradingChatClient,
                textExtractionService,
                rubricValidator,
                notificationService,
                new ObjectMapper(),
                eventPublisher
        );

        Assignment assignment = Assignment.builder()
                .courseId(3L)
                .title("期末作业")
                .build();
        assignment.setId(9L);
        AiGradingConfig config = AiGradingConfig.builder()
                .assignmentId(9L)
                .enabled(true)
                .rubricJson("[]")
                .build();
        Submission submission = Submission.builder()
                .id(11L)
                .assignmentId(9L)
                .studentId(21L)
                .content("提交内容")
                .build();
        GradingBatchTask savedTask = GradingBatchTask.builder()
                .assignmentId(9L)
                .teacherId(5L)
                .status(GradingBatchTaskStatus.PENDING)
                .totalCount(1)
                .build();
        savedTask.setId(124L);

        when(submissionRepository.findById(11L)).thenReturn(Optional.of(submission));
        when(assignmentRepository.findById(9L)).thenReturn(Optional.of(assignment));
        when(courseMemberRepository.findByCourseIdAndUserId(3L, 5L))
                .thenReturn(Optional.of(CourseMember.builder()
                        .courseId(3L)
                        .userId(5L)
                        .role(CourseMemberRole.TEACHER)
                        .build()));
        when(configRepository.findByAssignmentId(9L)).thenReturn(Optional.of(config));
        when(batchTaskRepository.save(any(GradingBatchTask.class))).thenReturn(savedTask);

        GradingBatchTask result = service.gradeSubmissionAsyncTask(11L, 5L);

        assertEquals(124L, result.getId());
        assertEquals(GradingBatchTaskStatus.PENDING, result.getStatus());
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event ->
                event instanceof GradingBatchTaskRequestedEvent requested
                        && requested.taskId().equals(124L)
                        && requested.submissionIds().equals(List.of(11L))));
        verify(batchTaskRepository, never()).findById(124L);
    }
}
