package com.ketangpai.dto.course;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CourseSortRequest(
        @NotEmpty(message = "排序列表不能为空")
        @Size(max = 100, message = "单次最多调整100门课程")
        List<@Valid CourseSortItem> items
) {
}
