package com.ketangpai.service;

import com.ketangpai.dto.user.UserResponse;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.entity.User;
import com.ketangpai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse updateProfile(Long userId, String realName, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new BusinessException(409, "邮箱已被注册");
        }

        if (realName != null) user.setRealName(realName);
        if (email != null) user.setEmail(email);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(400, "原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public User updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        user.setAvatarUrl(avatarUrl);
        return userRepository.save(user);
    }

    /**
     * 获取用户头像的 MinIO 对象路径（存储在 avatar_url 字段中）。
     *
     * @return objectPath，不存在则返回 null
     */
    @Transactional(readOnly = true)
    public String getAvatarPath(Long userId) {
        return userRepository.findById(userId)
                .map(User::getAvatarUrl)
                .orElse(null);
    }
}
