package com.ketangpai.service;

import com.ketangpai.dto.auth.LoginRequest;
import com.ketangpai.dto.auth.LoginResponse;
import com.ketangpai.dto.auth.RegisterRequest;
import com.ketangpai.dto.auth.RegisterResponse;
import com.ketangpai.dto.user.UserResponse;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.entity.User;
import com.ketangpai.repository.UserRepository;
import com.ketangpai.security.AuthenticationAuditService;
import com.ketangpai.security.JwtUtil;
import com.ketangpai.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 账号认证服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$ABzkpiV1yymgUtANwY4uEusXqLkww4FzLU0w2wGoO0t8fiRRmZNwu";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final AuthenticationAuditService authenticationAuditService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(409, "用户名已存在");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new BusinessException(409, "邮箱已被注册");
        }

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .email(email)
                .realName(normalizeOptional(request.realName()))
                .role(request.role())
                .enabled(true)
                .deleted(false)
                .build();

        User saved = userRepository.save(user);
        authenticationAuditService.registrationSucceeded(saved);
        return new RegisterResponse(saved.getId(), saved.getUsername(), saved.getRole());
    }

    /** 登录并返回 JWT Token 和用户信息 */
    public LoginResponse login(LoginRequest request, String clientIp) {
        try {
            loginAttemptService.checkAllowed(request.username(), clientIp);
        } catch (BusinessException e) {
            authenticationAuditService.loginBlocked(request.username(), clientIp);
            throw e;
        }

        User user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw invalidCredentials(request.username(), clientIp);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw invalidCredentials(request.username(), clientIp);
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            loginAttemptService.recordFailure(request.username(), clientIp);
            authenticationAuditService.loginFailed(
                    request.username(), clientIp, AuthenticationAuditService.FailureReason.ACCOUNT_DISABLED);
            throw new BusinessException(403, "账号已被禁用");
        }

        loginAttemptService.recordSuccess(request.username(), clientIp);
        authenticationAuditService.loginSucceeded(user, clientIp);
        String token = jwtUtil.generateToken(user.getId(), user.getRole().name(), user.getPassword());
        return new LoginResponse(token, UserResponse.from(user));
    }

    private BusinessException invalidCredentials(String username, String clientIp) {
        loginAttemptService.recordFailure(username, clientIp);
        authenticationAuditService.loginFailed(
                username, clientIp, AuthenticationAuditService.FailureReason.INVALID_CREDENTIALS);
        return new BusinessException(401, "用户名或密码错误");
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
        return UserResponse.from(user);
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeOptional(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
