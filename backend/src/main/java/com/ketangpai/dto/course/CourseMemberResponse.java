package com.ketangpai.dto.course;

import com.ketangpai.model.enums.CourseMemberRole;

import java.time.LocalDateTime;

public record CourseMemberResponse(
        Long id,
        Long userId,
        String username,
        String realName,
        String avatarUrl,
        CourseMemberRole role,
        LocalDateTime joinedAt
) {
}
