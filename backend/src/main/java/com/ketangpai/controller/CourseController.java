package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.model.entity.Course;
import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.security.CurrentUserId;
import com.ketangpai.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 课程管理 Controller
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public Result<List<CourseMember>> listMyCourses(@CurrentUserId Long userId,
                                                     @RequestParam(defaultValue = "false") boolean archived) {
        return Result.ok(courseService.listMyCourses(userId, archived));
    }

    @PostMapping
    public Result<Course> create(@CurrentUserId Long userId, @RequestBody Map<String, String> body) {
        return Result.ok(courseService.createCourse(userId,
                body.get("name"), body.get("description"), body.get("coverUrl")));
    }

    @PostMapping("/join")
    public Result<CourseMember> join(@CurrentUserId Long userId, @RequestBody Map<String, String> body) {
        return Result.ok(courseService.joinByCode(userId, body.get("courseCode")));
    }

    @GetMapping("/{courseId}")
    public Result<Course> getDetail(@CurrentUserId Long userId, @PathVariable Long courseId) {
        return Result.ok(courseService.getDetail(courseId, userId));
    }

    @GetMapping("/{courseId}/members")
    public Result<List<CourseMember>> getMembers(@CurrentUserId Long userId,
                                                  @PathVariable Long courseId,
                                                  @RequestParam(required = false) String role) {
        List<CourseMember> members = courseService.getMemberList(courseId, userId, role);
        return Result.ok(members);
    }

    @PutMapping("/{courseId}")
    public Result<Course> update(@CurrentUserId Long userId,
                                  @PathVariable Long courseId,
                                  @RequestBody Map<String, String> body) {
        return Result.ok(courseService.updateCourse(courseId, userId,
                body.get("name"), body.get("description"), body.get("coverUrl")));
    }

    @PostMapping("/{courseId}/action")
    public Result<Void> action(@CurrentUserId Long userId,
                                @PathVariable Long courseId,
                                @RequestBody Map<String, String> body) {
        courseService.performAction(courseId, userId, body.get("action"));
        return Result.ok();
    }

}
