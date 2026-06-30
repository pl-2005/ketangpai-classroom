package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.dto.course.CourseActionRequest;
import com.ketangpai.dto.course.CourseCardResponse;
import com.ketangpai.dto.course.CourseDetailResponse;
import com.ketangpai.dto.course.CourseMemberResponse;
import com.ketangpai.dto.course.CourseMembershipResponse;
import com.ketangpai.dto.course.CreateCourseRequest;
import com.ketangpai.dto.course.JoinCourseRequest;
import com.ketangpai.dto.course.UpdateCourseMemberRoleRequest;
import com.ketangpai.dto.course.UpdateCourseRequest;
import com.ketangpai.dto.course.UpdateSortOrderRequest;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 课程管理 Controller。
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public Result<Page<CourseCardResponse>> listMyCourses(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "false") boolean archived,
            @PageableDefault(size = 12) Pageable pageable) {
        return Result.ok(courseService.listMyCourses(userId, archived, pageable));
    }

    @PostMapping
    public ResponseEntity<Result<CourseDetailResponse>> create(
            @CurrentUserId Long userId,
            @Valid @RequestBody CreateCourseRequest request) {
        CourseDetailResponse course = courseService.createCourse(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok("课程创建成功", course));
    }

    @PostMapping("/join")
    public Result<CourseMembershipResponse> join(
            @CurrentUserId Long userId,
            @Valid @RequestBody JoinCourseRequest request) {
        CourseMember member = courseService.joinByCode(userId, request.courseCode());
        return Result.ok(new CourseMembershipResponse(
                member.getCourseId(), member.getRole(), member.getIsArchived()));
    }

    @GetMapping("/{courseId}")
    public Result<CourseDetailResponse> getDetail(
            @CurrentUserId Long userId,
            @PathVariable Long courseId) {
        return Result.ok(courseService.getDetail(courseId, userId));
    }

    @GetMapping("/{courseId}/members")
    public Result<Page<CourseMemberResponse>> getMembers(
            @CurrentUserId Long userId,
            @PathVariable Long courseId,
            @RequestParam(required = false) String role,
            @PageableDefault(size = 30) Pageable pageable) {
        return Result.ok(courseService.getMemberList(
                courseId, userId, parseRole(role), pageable));
    }

    @PutMapping("/{courseId}")
    public Result<CourseDetailResponse> update(
            @CurrentUserId Long userId,
            @PathVariable Long courseId,
            @Valid @RequestBody UpdateCourseRequest request) {
        return Result.ok(courseService.updateCourse(courseId, userId, request));
    }

    @PutMapping("/{courseId}/members/{memberUserId}/role")
    public Result<Void> updateMemberRole(
            @CurrentUserId Long userId,
            @PathVariable Long courseId,
            @PathVariable Long memberUserId,
            @Valid @RequestBody UpdateCourseMemberRoleRequest request) {
        courseService.updateMemberRole(courseId, userId, memberUserId, request.role());
        return Result.ok();
    }

    @PostMapping("/{courseId}/action")
    public Result<Void> action(
            @CurrentUserId Long userId,
            @PathVariable Long courseId,
            @Valid @RequestBody CourseActionRequest request) {
        courseService.performAction(courseId, userId, request.action());
        return Result.ok();
    }

    /** 批量更新课程卡片排序，courseIds 的顺序即为新的排序。 */
    @PutMapping("/sort-order")
    public Result<Void> updateSortOrder(
            @CurrentUserId Long userId,
            @Valid @RequestBody UpdateSortOrderRequest request) {
        courseService.updateSortOrder(userId, request.courseIds());
        return Result.ok();
    }

    private CourseMemberRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return CourseMemberRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "成员角色应为 CREATOR、TEACHER 或 STUDENT");
        }
    }
}
