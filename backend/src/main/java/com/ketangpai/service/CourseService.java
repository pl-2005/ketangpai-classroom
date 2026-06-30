package com.ketangpai.service;

import com.ketangpai.dto.course.CourseCardResponse;
import com.ketangpai.dto.course.CourseDetailResponse;
import com.ketangpai.dto.course.CourseMemberResponse;
import com.ketangpai.dto.course.CreateCourseRequest;
import com.ketangpai.dto.course.UpdateCourseRequest;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.entity.Course;
import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.model.entity.User;
import com.ketangpai.model.enums.CourseAction;
import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.CourseStatus;
import com.ketangpai.model.enums.UserRole;
import com.ketangpai.repository.CourseMemberRepository;
import com.ketangpai.repository.CourseRepository;
import com.ketangpai.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课程管理服务。
 */
@Service
public class CourseService extends BaseService {

    private static final String COURSE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int COURSE_CODE_LENGTH = 6;
    private static final int COURSE_CODE_MAX_ATTEMPTS = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseService(CourseMemberRepository courseMemberRepository,
                         CourseRepository courseRepository,
                         UserRepository userRepository) {
        super(courseMemberRepository);
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    /** 获取当前用户的课程卡片，区分正常视图和归档视图。 */
    @Transactional(readOnly = true)
    public Page<CourseCardResponse> listMyCourses(Long userId, boolean archived, Pageable pageable) {
        return courseMemberRepository.findCourseCards(userId, archived, pageable);
    }

    /** 只有全局角色为教师的用户可以创建课程。 */
    @Transactional
    public CourseDetailResponse createCourse(Long userId, CreateCourseRequest request) {
        User creator = getEnabledUserOrThrow(userId);
        if (creator.getRole() != UserRole.TEACHER) {
            throw new BusinessException(403, "仅教师可以创建课程");
        }

        Course course = Course.builder()
                .name(normalizeRequired(request.name(), "课程名称不能为空"))
                .description(normalizeNullable(request.description()))
                .courseCode(generateUniqueCourseCode())
                .coverUrl(normalizeNullable(request.coverUrl()))
                .status(CourseStatus.ACTIVE)
                .creatorId(userId)
                .build();
        course = courseRepository.save(course);

        CourseMember member = CourseMember.builder()
                .courseId(course.getId())
                .userId(userId)
                .role(CourseMemberRole.CREATOR)
                .build();
        courseMemberRepository.save(member);

        return toDetail(course, CourseMemberRole.CREATOR, 1L);
    }

    /**
     * 通过课程号加入课程。所有用户先以学生权限加入；教师管理权限由创建者明确授予。
     */
    @Transactional
    public CourseMember joinByCode(Long userId, String rawCourseCode) {
        String courseCode = normalizeCourseCode(rawCourseCode);
        Course course = courseRepository.findByCourseCode(courseCode)
                .orElseThrow(() -> new BusinessException(404, "课程不存在或课程号无效"));
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new BusinessException(400, "课程已归档，无法加入");
        }

        return courseMemberRepository.findByCourseIdAndUserId(course.getId(), userId)
                .map(member -> restoreFormerMember(member))
                .orElseGet(() -> courseMemberRepository.save(CourseMember.builder()
                        .courseId(course.getId())
                        .userId(userId)
                        .role(CourseMemberRole.STUDENT)
                        .isArchived(false)
                        .build()));
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse getDetail(Long courseId, Long userId) {
        CourseMember member = getMemberOrThrow(courseId, userId);
        Course course = getCourseOrThrow(courseId);
        long memberCount = courseMemberRepository.countActiveMembersByCourseId(courseId);
        return toDetail(course, member.getRole(), memberCount);
    }

    /** 获取课程成员展示信息。 */
    @Transactional(readOnly = true)
    public Page<CourseMemberResponse> getMemberList(Long courseId,
                                                     Long userId,
                                                     CourseMemberRole roleFilter,
                                                     Pageable pageable) {
        getMemberOrThrow(courseId, userId);
        getCourseOrThrow(courseId);
        return courseMemberRepository.findMemberResponses(courseId, roleFilter, pageable);
    }

    @Transactional
    public CourseDetailResponse updateCourse(Long courseId, Long userId, UpdateCourseRequest request) {
        CourseMember currentMember = getTeacherOrThrow(courseId, userId);
        Course course = getCourseOrThrow(courseId);

        if (request.name() != null) {
            course.setName(normalizeRequired(request.name(), "课程名称不能为空"));
        }
        if (request.description() != null) {
            course.setDescription(normalizeNullable(request.description()));
        }
        if (request.coverUrl() != null) {
            course.setCoverUrl(normalizeNullable(request.coverUrl()));
        }
        course = courseRepository.save(course);

        long memberCount = courseMemberRepository.countActiveMembersByCourseId(courseId);
        return toDetail(course, currentMember.getRole(), memberCount);
    }

    /** 创建者授予或撤销课程共管权限。 */
    @Transactional
    public CourseMember updateMemberRole(Long courseId,
                                         Long currentUserId,
                                         Long targetUserId,
                                         CourseMemberRole targetRole) {
        getCreatorOrThrow(courseId, currentUserId);
        if (targetRole != CourseMemberRole.TEACHER && targetRole != CourseMemberRole.STUDENT) {
            throw new BusinessException(400, "成员角色只能设置为 TEACHER 或 STUDENT");
        }

        CourseMember target = getMemberOrThrow(courseId, targetUserId);
        if (target.getRole() == CourseMemberRole.CREATOR) {
            throw new BusinessException(400, "不能修改课程创建者角色");
        }
        if (targetRole == CourseMemberRole.TEACHER) {
            User user = getEnabledUserOrThrow(targetUserId);
            if (user.getRole() != UserRole.TEACHER) {
                throw new BusinessException(400, "只有教师账号可以成为课程共管教师");
            }
        }

        target.setRole(targetRole);
        return courseMemberRepository.save(target);
    }

    @Transactional
    public void performAction(Long courseId, Long userId, CourseAction action) {
        switch (action) {
            case ARCHIVE -> archiveForSelf(courseId, userId);
            case UNARCHIVE -> unarchiveForSelf(courseId, userId);
            case ARCHIVE_FOR_ALL -> setCourseStatus(courseId, userId, CourseStatus.ARCHIVED);
            case RESTORE_FOR_ALL -> setCourseStatus(courseId, userId, CourseStatus.ACTIVE);
            case LEAVE -> leaveCourse(courseId, userId);
            case DELETE -> deleteCourse(courseId, userId);
        }
    }

    /**
     * 批量更新当前用户的课程卡片排序。
     * courseIds 列表的顺序即为新的排序顺序，从 0 开始递增赋值 sortOrder。
     */
    @Transactional
    public void updateSortOrder(Long userId, List<Long> courseIds) {
        List<CourseMember> members = courseMemberRepository.findByUserIdAndCourseIdIn(userId, courseIds);

        Map<Long, CourseMember> memberMap = members.stream()
                .collect(Collectors.toMap(CourseMember::getCourseId, cm -> cm));

        for (Long courseId : courseIds) {
            CourseMember cm = memberMap.get(courseId);
            if (cm == null) {
                throw new BusinessException(403, "你不是课程 " + courseId + " 的成员");
            }
        }

        for (int i = 0; i < courseIds.size(); i++) {
            CourseMember cm = memberMap.get(courseIds.get(i));
            cm.setSortOrder(i);
        }

        courseMemberRepository.saveAll(members);
    }

    private CourseMember restoreFormerMember(CourseMember member) {
        if (!member.getDeleted()) {
            throw new BusinessException(409, "你已加入该课程");
        }
        member.setDeleted(false);
        member.setIsArchived(false);
        member.setRole(CourseMemberRole.STUDENT);
        return courseMemberRepository.save(member);
    }

    private void archiveForSelf(Long courseId, Long userId) {
        CourseMember member = getMemberOrThrow(courseId, userId);
        member.setIsArchived(true);
        courseMemberRepository.save(member);
    }

    private void unarchiveForSelf(Long courseId, Long userId) {
        CourseMember member = getMemberOrThrow(courseId, userId);
        member.setIsArchived(false);
        courseMemberRepository.save(member);
    }

    private void setCourseStatus(Long courseId, Long userId, CourseStatus status) {
        getCreatorOrThrow(courseId, userId);
        Course course = getCourseOrThrow(courseId);
        course.setStatus(status);
        courseRepository.save(course);
    }

    private void leaveCourse(Long courseId, Long userId) {
        CourseMember member = getMemberOrThrow(courseId, userId);
        if (member.getRole() == CourseMemberRole.CREATOR) {
            throw new BusinessException(400, "创建者不能退课，请先删除课程");
        }
        member.setDeleted(true);
        member.setIsArchived(false);
        courseMemberRepository.save(member);
    }

    private void deleteCourse(Long courseId, Long userId) {
        getCreatorOrThrow(courseId, userId);
        Course course = getCourseOrThrow(courseId);
        course.setDeleted(true);
        courseRepository.save(course);
    }

    private Course getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(404, "课程不存在"));
    }

    private User getEnabledUserOrThrow(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(403, "账号已被禁用");
        }
        return user;
    }

    private CourseDetailResponse toDetail(Course course,
                                          CourseMemberRole currentUserRole,
                                          long memberCount) {
        return new CourseDetailResponse(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getCourseCode(),
                course.getCoverUrl(),
                course.getStatus(),
                course.getCreatorId(),
                currentUserRole,
                memberCount,
                course.getCreateTime(),
                course.getUpdateTime());
    }

    private String generateUniqueCourseCode() {
        for (int attempt = 0; attempt < COURSE_CODE_MAX_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(COURSE_CODE_LENGTH);
            for (int i = 0; i < COURSE_CODE_LENGTH; i++) {
                code.append(COURSE_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(COURSE_CODE_CHARS.length())));
            }
            String candidate = code.toString();
            if (!courseRepository.existsByCourseCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(500, "课程号生成失败，请稍后重试");
    }

    private String normalizeCourseCode(String rawCourseCode) {
        if (rawCourseCode == null) {
            throw new BusinessException(400, "课程号不能为空");
        }
        String courseCode = rawCourseCode.trim().toUpperCase(Locale.ROOT);
        if (!courseCode.matches("[A-Z0-9]{6}")) {
            throw new BusinessException(400, "课程号应为6位字母或数字");
        }
        return courseCode;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(400, message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
