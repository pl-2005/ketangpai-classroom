package com.ketangpai.service;

import com.ketangpai.model.entity.User;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.UserRole;
import com.ketangpai.repository.UserRepository;
import com.ketangpai.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 账号认证服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public User register(String username, String password, String email, String realName, UserRole role) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(409, "用户名已存在");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new BusinessException(409, "邮箱已被注册");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .realName(realName)
                .role(role)
                .enabled(true)
                .deleted(false)
                .build();

        return userRepository.save(user);
    }

    /** 登录并返回 JWT Token 和用户信息 */
    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));

        if (!user.getEnabled()) {
            throw new BusinessException(403, "账号已被禁用");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getRole().name());

        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "realName", user.getRealName(),
                        "role", user.getRole(),
                        "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
                )
        );
    }

    public User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));
    }
}
