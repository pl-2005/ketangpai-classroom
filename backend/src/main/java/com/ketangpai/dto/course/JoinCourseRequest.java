package com.ketangpai.dto.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinCourseRequest(
        @NotBlank(message = "课程号不能为空")
        @Pattern(regexp = "(?i)^[A-Z0-9]{6}$", message = "课程号应为6位字母或数字")
        String courseCode
) {
}
