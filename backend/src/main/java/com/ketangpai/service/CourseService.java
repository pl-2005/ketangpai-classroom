package com.ketangpai.service;

import com.ketangpai.model.entity.Course;
import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.CourseStatus;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.CourseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 课程管理服务
 */
@Service
public class CourseService extends BaseService {

    private final CourseRepository courseRepository;

    public CourseService(CourseMemberRepository courseMemberRepository, CourseRepository courseRepository) {
        super(courseMemberRepository);
        this.courseRepository = courseRepository;
    }

    /** 获取用户的有效课程列表（未归档） */
    public List<CourseMember> listMyCourses(Long userId, boolean archived) {
        List<CourseMember> members = courseMemberRepository.findByUserId(userId);
        if (archived) {
            return members.stream().filter(CourseMember::getIsArchived).toList();
        }
        return members.stream().filter(cm -> !cm.getIsArchived()).toList();
    }

    @Transactional
    public Course createCourse(Long userId, String name, String description, String coverUrl) {
        String courseCode = generateCourseCode();
        Course course = Course.builder()
                .name(name)
                .description(description)
                .courseCode(courseCode)
                .coverUrl(coverUrl)
                .status(CourseStatus.ACTIVE)
                .creatorId(userId)
                .build();
        course = courseRepository.save(course);

        // 创建者自动成为 CREATOR 角色成员
        CourseMember member = CourseMember.builder()
                .courseId(course.getId())
                .userId(userId)
                .role(CourseMemberRole.CREATOR)
                .build();
        courseMemberRepository.save(member);

        return course;
    }

    @Transactional
    public CourseMember joinByCode(Long userId, String courseCode) {
        Course course = courseRepository.findByCourseCode(courseCode)
                .orElseThrow(() -> new BusinessException(404, "课程不存在或课程号无效"));
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new BusinessException(400, "课程已归档，无法加入");
        }

        // 检查是否已有成员记录（含已退课）
        return courseMemberRepository.findByCourseIdAndUserId(course.getId(), userId)
                .map(cm -> {
                    if (!cm.getDeleted()) {
                        throw new BusinessException(409, "你已加入该课程");
                    }
                    // 曾退课，重新加入
                    cm.setDeleted(false);
                    cm.setRole(CourseMemberRole.STUDENT);
                    return courseMemberRepository.save(cm);
                })
                .orElseGet(() -> {
                    CourseMember cm = CourseMember.builder()
                            .courseId(course.getId())
                            .userId(userId)
                            .role(CourseMemberRole.STUDENT)
                            .build();
                    return courseMemberRepository.save(cm);
                });
    }

    public Course getDetail(Long courseId, Long userId) {
        getMemberOrThrow(courseId, userId);
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(404, "课程不存在"));
    }

    /** 获取课程成员列表（支持按角色筛选） */
    public List<CourseMember> getMemberList(Long courseId, Long userId, String roleFilter) {
        getMemberOrThrow(courseId, userId);
        List<CourseMember> members = courseMemberRepository.findByCourseId(courseId);
        if (roleFilter != null) {
            CourseMemberRole filterRole = CourseMemberRole.valueOf(roleFilter);
            members = members.stream().filter(m -> m.getRole() == filterRole).toList();
        }
        return members;
    }

    public Page<CourseMember> getMembers(Long courseId, Long userId, String roleFilter, Pageable pageable) {
        getMemberOrThrow(courseId, userId);
        // TODO: 实现分页查询成员
        List<CourseMember> members = courseMemberRepository.findByCourseId(courseId);
        if (roleFilter != null) {
            CourseMemberRole filterRole = CourseMemberRole.valueOf(roleFilter);
            members = members.stream().filter(m -> m.getRole() == filterRole).toList();
        }
        // 简化版：不分页，直接返回到前端处理
        return null;
    }

    @Transactional
    public Course updateCourse(Long courseId, Long userId, String name, String description, String coverUrl) {
        getTeacherOrThrow(courseId, userId);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(404, "课程不存在"));
        if (name != null) course.setName(name);
        if (description != null) course.setDescription(description);
        if (coverUrl != null) course.setCoverUrl(coverUrl);
        return courseRepository.save(course);
    }

    @Transactional
    public void performAction(Long courseId, Long userId, String action) {
        switch (action.toUpperCase()) {
            case "ARCHIVE" -> archiveForSelf(courseId, userId);
            case "UNARCHIVE" -> unarchiveForSelf(courseId, userId);
            case "ARCHIVE_FOR_ALL" -> archiveForAll(courseId, userId);
            case "LEAVE" -> leaveCourse(courseId, userId);
            case "DELETE" -> deleteCourse(courseId, userId);
            default -> throw new BusinessException(400, "不支持的操作：" + action);
        }
    }

    private void archiveForSelf(Long courseId, Long userId) {
        CourseMember cm = getMemberOrThrow(courseId, userId);
        cm.setIsArchived(true);
        courseMemberRepository.save(cm);
    }

    private void unarchiveForSelf(Long courseId, Long userId) {
        CourseMember cm = courseMemberRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new BusinessException(404, "未加入该课程"));
        cm.setIsArchived(false);
        courseMemberRepository.save(cm);
    }

    private void archiveForAll(Long courseId, Long userId) {
        getCreatorOrThrow(courseId, userId);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(404, "课程不存在"));
        course.setStatus(CourseStatus.ARCHIVED);
        courseRepository.save(course);
    }

    private void leaveCourse(Long courseId, Long userId) {
        CourseMember cm = getMemberOrThrow(courseId, userId);
        if (cm.getRole() == CourseMemberRole.CREATOR) {
            throw new BusinessException(400, "创建者不能退课，请转让课程或删除课程");
        }
        cm.setDeleted(true);
        courseMemberRepository.save(cm);
    }

    private void deleteCourse(Long courseId, Long userId) {
        getCreatorOrThrow(courseId, userId);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(404, "课程不存在"));
        course.setDeleted(true);
        courseRepository.save(course);
    }

    /** 生成 6 位随机课程号 */
    private String generateCourseCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        String code = sb.toString();
        // 冲突检查
        if (courseRepository.existsByCourseCode(code)) {
            return generateCourseCode();
        }
        return code;
    }
}
