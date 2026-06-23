package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.User;
import com.ketangpai.model.enums.UserRole;
import com.ketangpai.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 账号认证 Controller
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        User user = authService.register(
                (String) body.get("username"),
                (String) body.get("password"),
                (String) body.get("email"),
                (String) body.get("realName"),
                UserRole.valueOf((String) body.get("role"))
        );
        return Result.ok(Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole()));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        User user = authService.login(body.get("username"), body.get("password"));
        // TODO: 生成 JWT Token
        String token = "TODO-jwt-token";
        return Result.ok(Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "realName", user.getRealName(),
                        "role", user.getRole(),
                        "avatarUrl", user.getAvatarUrl()
                )
        ));
    }

    @GetMapping("/me")
    public Result<User> me() {
        // TODO: 从 SecurityContext 获取当前用户 ID
        Long userId = 1L; // 占位
        return Result.ok(authService.getCurrentUser(userId));
    }
}
