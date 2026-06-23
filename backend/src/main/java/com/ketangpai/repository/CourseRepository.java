package com.ketangpai.repository;

import com.ketangpai.entity.Course;
import com.ketangpai.model.enums.CourseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 课程 Repository
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCourseCode(String courseCode);

    List<Course> findByCreatorId(Long creatorId);

    List<Course> findByStatus(CourseStatus status);

    boolean existsByCourseCode(String courseCode);
}
