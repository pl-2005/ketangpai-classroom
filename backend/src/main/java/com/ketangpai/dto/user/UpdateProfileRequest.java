package com.ketangpai.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 50, message = "真实姓名长度不能超过50个字符")
        String realName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱长度不能超过100个字符")
        String email
) {
}
