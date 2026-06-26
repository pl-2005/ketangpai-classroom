package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.dto.user.ChangePasswordRequest;
import com.ketangpai.dto.user.UpdateProfileRequest;
import com.ketangpai.dto.user.UserResponse;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.FileService;
import com.ketangpai.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 用户管理 Controller
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FileService fileService;

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

    /**
     * 上传头像。
     * 接收图片文件 → 上传到 MinIO → 更新 user.avatar_url（存储 objectPath）→ 返回预签名 URL。
     */
    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(@CurrentUserId Long userId,
                                                     @RequestParam("file") MultipartFile file)
            throws IOException {
        Map<String, String> result = fileService.uploadAvatar(
                file.getBytes(),
                file.getOriginalFilename(),
                userId);
        // 将 MinIO 对象路径存入数据库
        userService.updateAvatar(userId, result.get("objectPath"));
        // 返回预签名 URL 供前端即时展示
        return Result.ok("头像上传成功", Map.of("avatarUrl", result.get("avatarUrl")));
    }

    /**
     * 获取当前用户的头像预签名 URL。
     * 前端刷新页面后调用此接口获取新鲜的预签名 URL 用于 <img> 展示。
     */
    @GetMapping("/avatar")
    public Result<Map<String, String>> getAvatarUrl(@CurrentUserId Long userId) {
        // 从数据库读取存储的 objectPath
        String objectPath = userService.getAvatarPath(userId);
        String avatarUrl = fileService.getAvatarPresignedUrl(objectPath);
        return Result.ok(Map.of("avatarUrl", avatarUrl != null ? avatarUrl : ""));
    }
}
