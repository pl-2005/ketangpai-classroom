package com.ketangpai.dto.auth;

import com.ketangpai.dto.user.UserResponse;

public record LoginResponse(String token, UserResponse user) {
}
