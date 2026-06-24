package com.ketangpai.repository;

import com.ketangpai.model.entity.Course;
import com.ketangpai.model.enums.CourseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    @Query(value = """
            SELECT *
            FROM course
            WHERE creator_id = :creatorId AND deleted = 1
            ORDER BY update_time DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM course
            WHERE creator_id = :creatorId AND deleted = 1
            """,
            nativeQuery = true)
    Page<Course> findDeletedByCreatorId(@Param("creatorId") Long creatorId, Pageable pageable);

    @Query(value = """
            SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
            FROM course
            WHERE id = :courseId AND creator_id = :creatorId AND deleted = 1
            """, nativeQuery = true)
    boolean existsDeletedByIdAndCreatorId(@Param("courseId") Long courseId,
                                           @Param("creatorId") Long creatorId);

    @Modifying
    @Query(value = """
            UPDATE course
            SET deleted = 0, update_time = CURRENT_TIMESTAMP
            WHERE id = :courseId AND creator_id = :creatorId AND deleted = 1
            """, nativeQuery = true)
    int restoreDeletedCourse(@Param("courseId") Long courseId,
                             @Param("creatorId") Long creatorId);
}
