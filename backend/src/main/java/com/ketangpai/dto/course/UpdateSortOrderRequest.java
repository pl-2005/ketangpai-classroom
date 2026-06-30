package com.ketangpai.dto.course;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 课程卡片排序更新请求。
 * courseIds 列表的顺序即为用户期望的新排序，列表索引映射为 sortOrder 值（0, 1, 2, ...）。
 */
public record UpdateSortOrderRequest(
        @NotEmpty(message = "课程ID列表不能为空")
        List<Long> courseIds
) {
}
