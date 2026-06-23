package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.User;
import com.ketangpai.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户管理 Controller
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/profile")
    public Result<User> updateProfile(@RequestBody Map<String, String> body) {
        Long userId = 1L; // TODO: 从 SecurityContext 获取
        return Result.ok(userService.updateProfile(userId, body.get("realName"), body.get("email")));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        Long userId = 1L;
        userService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }
}
