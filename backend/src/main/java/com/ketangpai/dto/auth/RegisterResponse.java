package com.ketangpai.dto.auth;

import com.ketangpai.model.enums.UserRole;

public record RegisterResponse(Long id, String username, UserRole role) {
}
