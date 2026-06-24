package com.ketangpai.dto.course;

import jakarta.validation.constraints.Size;

public record UpdateCourseRequest(
        @Size(min = 1, max = 100, message = "课程名称长度应为1到100个字符")
        String name,

        @Size(max = 5000, message = "课程简介不能超过5000个字符")
        String description,

        @Size(max = 255, message = "封面地址不能超过255个字符")
        String coverUrl
) {
}
