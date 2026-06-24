package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.dto.user.ChangePasswordRequest;
import com.ketangpai.dto.user.UpdateProfileRequest;
import com.ketangpai.dto.user.UserResponse;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理 Controller
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/profile")
    public Result<UserResponse> updateProfile(@CurrentUserId Long userId,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(userId, request.realName(), request.email()));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@CurrentUserId Long userId,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request.oldPassword(), request.newPassword());
        return Result.ok();
    }
}
