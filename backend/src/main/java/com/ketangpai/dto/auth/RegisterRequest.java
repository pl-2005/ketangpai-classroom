package com.ketangpai.dto.auth;

import com.ketangpai.model.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,49}$",
                message = "用户名须为4-50个字符，以字母开头且只能包含字母、数字和下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,32}$",
                message = "密码须为8-32个字符，且包含大写字母、小写字母和数字")
        String password,

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱长度不能超过100个字符")
        String email,

        @Size(max = 50, message = "真实姓名长度不能超过50个字符")
        String realName,

        @NotNull(message = "用户角色不能为空")
        UserRole role
) {
}
