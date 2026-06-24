package com.ketangpai.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空")
        @Size(max = 256, message = "原密码长度不能超过256个字符")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,32}$",
                message = "新密码须为8-32个字符，且包含大写字母、小写字母和数字")
        String newPassword
) {
}
