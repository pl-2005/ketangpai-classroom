package com.ketangpai.dto.user;

import com.ketangpai.model.entity.User;
import com.ketangpai.model.enums.UserRole;

public record UserResponse(
        Long id,
        String username,
        String realName,
        String email,
        UserRole role,
        String avatarUrl
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getEmail(),
                user.getRole(),
                user.getAvatarUrl()
        );
    }
}
