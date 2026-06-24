package com.ketangpai.service;

import com.ketangpai.dto.course.CourseCardResponse;
import com.ketangpai.dto.course.CreateCourseRequest;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseMemberRepository courseMemberRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void studentCannotCreateCourse() {
        CourseService service = service();
        User student = User.builder().role(UserRole.STUDENT).enabled(true).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.createCourse(
                2L, new CreateCourseRequest("软件工程", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);

        verify(courseRepository, never()).save(any());
    }

    @Test
    void teacherJoinsAsStudentUntilCreatorGrantsManagementRole() {
        CourseService service = service();
        Course course = course(10L, CourseStatus.ACTIVE);
        when(courseRepository.findByCourseCode("SE2026")).thenReturn(Optional.of(course));
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 2L)).thenReturn(Optional.empty());
        when(courseMemberRepository.save(any(CourseMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CourseMember joined = service.joinByCode(2L, " se2026 ");

        assertThat(joined.getRole()).isEqualTo(CourseMemberRole.STUDENT);
        assertThat(joined.getIsArchived()).isFalse();
    }

    @Test
    void creatorCanPromoteAJoinedTeacherAccount() {
        CourseService service = service();
        CourseMember creator = member(10L, 1L, CourseMemberRole.CREATOR);
        CourseMember target = member(10L, 2L, CourseMemberRole.STUDENT);
        User teacher = User.builder().role(UserRole.TEACHER).enabled(true).build();
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(creator));
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(target));
        when(userRepository.findById(2L)).thenReturn(Optional.of(teacher));
        when(courseMemberRepository.save(target)).thenReturn(target);

        CourseMember updated = service.updateMemberRole(
                10L, 1L, 2L, CourseMemberRole.TEACHER);

        assertThat(updated.getRole()).isEqualTo(CourseMemberRole.TEACHER);
    }

    @Test
    void studentAccountCannotBePromotedToCourseTeacher() {
        CourseService service = service();
        CourseMember creator = member(10L, 1L, CourseMemberRole.CREATOR);
        CourseMember target = member(10L, 2L, CourseMemberRole.STUDENT);
        User student = User.builder().role(UserRole.STUDENT).enabled(true).build();
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(creator));
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(target));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.updateMemberRole(
                10L, 1L, 2L, CourseMemberRole.TEACHER))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void creatorCanArchiveAndRestoreCourseForEveryone() {
        CourseService service = service();
        CourseMember creator = member(10L, 1L, CourseMemberRole.CREATOR);
        Course course = course(10L, CourseStatus.ACTIVE);
        when(courseMemberRepository.findByCourseIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(creator));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        service.performAction(10L, 1L, CourseAction.ARCHIVE_FOR_ALL);
        assertThat(course.getStatus()).isEqualTo(CourseStatus.ARCHIVED);

        service.performAction(10L, 1L, CourseAction.RESTORE_FOR_ALL);
        assertThat(course.getStatus()).isEqualTo(CourseStatus.ACTIVE);
    }

    @Test
    void courseListUsesRepositoryProjectionAndPagination() {
        CourseService service = service();
        PageRequest pageable = PageRequest.of(0, 12);
        CourseCardResponse card = new CourseCardResponse(
                10L, "软件工程", "SE2026", null, CourseStatus.ACTIVE,
                35L, CourseMemberRole.CREATOR, false, 0, null);
        Page<CourseCardResponse> expected = new PageImpl<>(List.of(card), pageable, 1);
        when(courseMemberRepository.findCourseCards(1L, false, pageable)).thenReturn(expected);

        assertThat(service.listMyCourses(1L, false, pageable)).isSameAs(expected);
    }

    private CourseService service() {
        return new CourseService(courseMemberRepository, courseRepository, userRepository);
    }

    private Course course(Long id, CourseStatus status) {
        Course course = Course.builder()
                .name("软件工程")
                .courseCode("SE2026")
                .creatorId(1L)
                .status(status)
                .build();
        course.setId(id);
        return course;
    }

    private CourseMember member(Long courseId, Long userId, CourseMemberRole role) {
        return CourseMember.builder()
                .courseId(courseId)
                .userId(userId)
                .role(role)
                .deleted(false)
                .isArchived(false)
                .build();
    }
}
