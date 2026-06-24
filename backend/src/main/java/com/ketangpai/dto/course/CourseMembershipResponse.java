package com.ketangpai.dto.course;

import com.ketangpai.model.enums.CourseMemberRole;

public record CourseMembershipResponse(
        Long courseId,
        CourseMemberRole role,
        Boolean isArchived
) {
}
