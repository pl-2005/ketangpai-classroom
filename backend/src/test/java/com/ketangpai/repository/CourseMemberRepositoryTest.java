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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:course-test;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CourseMemberRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseMemberRepository courseMemberRepository;

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
                course.getId(), null, PageRequest.of(0, 30));

        assertThat(result.getContent())
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.userId()).isEqualTo(teacher.getId());
                    assertThat(member.username()).isEqualTo("teacher2");
                    assertThat(member.role()).isEqualTo(CourseMemberRole.CREATOR);
                });
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
