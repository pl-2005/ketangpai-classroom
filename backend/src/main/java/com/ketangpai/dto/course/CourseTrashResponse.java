package com.ketangpai.dto.course;

import com.ketangpai.model.enums.CourseStatus;

import java.time.LocalDateTime;

public record CourseTrashResponse(
        Long id,
        String name,
        String courseCode,
        String coverUrl,
        CourseStatus status,
        LocalDateTime deletedAt
) {
}
