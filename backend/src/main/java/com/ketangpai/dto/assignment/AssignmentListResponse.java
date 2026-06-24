package com.ketangpai.dto.assignment;

import com.ketangpai.model.enums.AssignmentStatus;
import com.ketangpai.model.enums.SubmissionStatus;

import java.time.LocalDateTime;

public record AssignmentListResponse(
        Long id,
        Long courseId,
        String title,
        AssignmentStatus status,
        LocalDateTime deadline,
        Integer maxScore,
        Boolean allowResubmit,
        SubmissionStatus mySubmissionStatus,
        LocalDateTime createTime
) {
}
