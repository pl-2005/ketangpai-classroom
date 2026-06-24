package com.ketangpai.dto.course;

import com.ketangpai.model.enums.CourseAction;
import jakarta.validation.constraints.NotNull;

public record CourseActionRequest(
        @NotNull(message = "课程操作不能为空")
        CourseAction action
) {
}
