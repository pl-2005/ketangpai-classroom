package com.ketangpai.dto.course;

import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.CourseStatus;

import java.time.LocalDateTime;

public record CourseCardResponse(
        Long id,
        String name,
        String courseCode,
        String coverUrl,
        CourseStatus status,
        Long memberCount,
        CourseMemberRole role,
        Boolean isArchived,
        Integer sortOrder,
        LocalDateTime createTime
) {
}
