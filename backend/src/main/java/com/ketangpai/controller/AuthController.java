package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.dto.auth.LoginRequest;
import com.ketangpai.dto.auth.LoginResponse;
import com.ketangpai.dto.auth.RegisterRequest;
import com.ketangpai.dto.auth.RegisterResponse;
import com.ketangpai.dto.user.UserResponse;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号认证 Controller
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Result<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok("注册成功", response));
    }

    @PostMapping("/login")
    public ResponseEntity<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                        HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(Result.ok("登录成功", response));
    }

    @GetMapping("/me")
    public Result<UserResponse> me(@CurrentUserId Long userId) {
        return Result.ok(authService.getCurrentUser(userId));
    }
}
