package com.ketangpai.dto.course;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CourseSortItem(
        @NotNull(message = "课程 ID 不能为空")
        Long courseId,

        @NotNull(message = "排序值不能为空")
        @Min(value = 0, message = "排序值不能小于0")
        Integer sortOrder
) {
}
