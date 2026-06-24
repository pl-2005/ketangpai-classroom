package com.ketangpai.dto.course;

import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.UserRole;

import java.time.LocalDateTime;

public record CourseMemberResponse(
        Long id,
        Long userId,
        String username,
        String realName,
        String avatarUrl,
        UserRole accountRole,
        CourseMemberRole role,
        LocalDateTime joinedAt
) {
}
