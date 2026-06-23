package com.ketangpai.dto.course;

import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.CourseStatus;

import java.time.LocalDateTime;

public record CourseDetailResponse(
        Long id,
        String name,
        String description,
        String courseCode,
        String coverUrl,
        CourseStatus status,
        Long creatorId,
        CourseMemberRole currentUserRole,
        Long memberCount,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
