package com.ketangpai.repository;

import com.ketangpai.dto.course.CourseCardResponse;
import com.ketangpai.dto.course.CourseMemberResponse;
import com.ketangpai.model.entity.Course;
import com.ketangpai.model.entity.CourseMember;
import com.ketangpai.model.entity.User;
import com.ketangpai.model.enums.CourseMemberRole;
import com.ketangpai.model.enums.CourseStatus;
import com.ketangpai.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:course-test;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CoursePurgeRepository.class)
class CourseMemberRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseMemberRepository courseMemberRepository;

    @Autowired
    private CoursePurgeRepository coursePurgeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void globalArchiveMovesCourseFromActiveViewToArchivedView() {
        User teacher = saveUser("teacher", UserRole.TEACHER);
        Course course = saveCourse(teacher.getId(), CourseStatus.ARCHIVED);
        saveMember(course.getId(), teacher.getId(), CourseMemberRole.CREATOR, false);

        Page<CourseCardResponse> active = courseMemberRepository.findCourseCards(
                teacher.getId(), false, PageRequest.of(0, 12));
        Page<CourseCardResponse> archived = courseMemberRepository.findCourseCards(
                teacher.getId(), true, PageRequest.of(0, 12));

        assertThat(active).isEmpty();
        assertThat(archived.getContent())
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.id()).isEqualTo(course.getId());
                    assertThat(card.memberCount()).isEqualTo(1L);
                    assertThat(card.status()).isEqualTo(CourseStatus.ARCHIVED);
                });
    }

    @Test
    void memberProjectionContainsUserProfile() {
        User teacher = saveUser("teacher2", UserRole.TEACHER);
        Course course = saveCourse(teacher.getId(), CourseStatus.ACTIVE);
        saveMember(course.getId(), teacher.getId(), CourseMemberRole.CREATOR, false);

        Page<CourseMemberResponse> result = courseMemberRepository.findMemberResponses(
                course.getId(), null, null, PageRequest.of(0, 30));

        assertThat(result.getContent())
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.userId()).isEqualTo(teacher.getId());
                    assertThat(member.username()).isEqualTo("teacher2");
                    assertThat(member.accountRole()).isEqualTo(UserRole.TEACHER);
                    assertThat(member.role()).isEqualTo(CourseMemberRole.CREATOR);
                });
    }

    @Test
    void memberProjectionSupportsRoleAndNameFiltering() {
        User creator = saveUser("course-owner", UserRole.TEACHER);
        User teacher = saveUser("assistant", UserRole.TEACHER);
        teacher.setRealName("王老师");
        userRepository.saveAndFlush(teacher);
        User student = saveUser("wang-student", UserRole.STUDENT);
        student.setRealName("王同学");
        userRepository.saveAndFlush(student);
        Course course = saveCourse(creator.getId(), CourseStatus.ACTIVE);
        saveMember(course.getId(), creator.getId(), CourseMemberRole.CREATOR, false);
        saveMember(course.getId(), teacher.getId(), CourseMemberRole.TEACHER, false);
        saveMember(course.getId(), student.getId(), CourseMemberRole.STUDENT, false);

        Page<CourseMemberResponse> result = courseMemberRepository.findMemberResponses(
                course.getId(), CourseMemberRole.TEACHER, "王", PageRequest.of(0, 30));

        assertThat(result.getContent())
                .extracting(CourseMemberResponse::userId)
                .containsExactly(teacher.getId());
    }

    @Test
    void deletedCourseCanBeListedRestoredAndPermanentlyPurged() {
        User teacher = saveUser("teacher3", UserRole.TEACHER);
        Course course = saveCourse(teacher.getId(), CourseStatus.ACTIVE);
        saveMember(course.getId(), teacher.getId(), CourseMemberRole.CREATOR, false);
        jdbcTemplate.update("UPDATE course SET deleted = 1 WHERE id = ?", course.getId());
        entityManager.clear();

        assertThat(courseRepository.findDeletedByCreatorId(
                teacher.getId(), PageRequest.of(0, 12)).getContent())
                .extracting(Course::getId)
                .containsExactly(course.getId());
        assertThat(courseRepository.existsDeletedByIdAndCreatorId(
                course.getId(), teacher.getId())).isTrue();

        assertThat(courseRepository.restoreDeletedCourse(
                course.getId(), teacher.getId())).isEqualTo(1);
        entityManager.clear();
        assertThat(courseRepository.findById(course.getId())).isPresent();

        jdbcTemplate.update("UPDATE course SET deleted = 1 WHERE id = ?", course.getId());
        coursePurgeRepository.purge(course.getId());

        Integer courseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course WHERE id = ?", Integer.class, course.getId());
        Integer memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_member WHERE course_id = ?",
                Integer.class, course.getId());
        assertThat(courseCount).isZero();
        assertThat(memberCount).isZero();
    }

    private User saveUser(String username, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .password("bcrypt-hash")
                .role(role)
                .enabled(true)
                .deleted(false)
                .build());
    }

    private Course saveCourse(Long creatorId, CourseStatus status) {
        return courseRepository.saveAndFlush(Course.builder()
                .name("软件工程")
                .courseCode("CODE" + creatorId)
                .status(status)
                .creatorId(creatorId)
                .deleted(false)
                .build());
    }

    private void saveMember(Long courseId,
                            Long userId,
                            CourseMemberRole role,
                            boolean archived) {
        courseMemberRepository.saveAndFlush(CourseMember.builder()
                .courseId(courseId)
                .userId(userId)
                .role(role)
                .isArchived(archived)
                .deleted(false)
                .build());
    }
}
