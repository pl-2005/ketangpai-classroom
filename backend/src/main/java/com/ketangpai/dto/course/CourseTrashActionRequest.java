package com.ketangpai.dto.course;

import com.ketangpai.model.enums.CourseTrashAction;
import jakarta.validation.constraints.NotNull;

public record CourseTrashActionRequest(
        @NotNull(message = "回收站操作不能为空")
        CourseTrashAction action
) {
}
