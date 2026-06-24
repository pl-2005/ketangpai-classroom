package com.ketangpai.dto.course;

import com.ketangpai.model.enums.CourseMemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateCourseMemberRoleRequest(
        @NotNull(message = "成员角色不能为空")
        CourseMemberRole role
) {
}
