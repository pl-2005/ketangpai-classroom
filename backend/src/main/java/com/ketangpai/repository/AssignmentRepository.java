package com.ketangpai.repository;

import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作业 Repository
 */
@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByCourseIdOrderByDeadlineAsc(Long courseId);

    List<Assignment> findByCourseIdAndStatus(Long courseId, AssignmentStatus status);

    /** 查询某课程下即将截止的作业（截止时间在指定时间之前且已发布） */
    List<Assignment> findByCourseIdAndStatusAndDeadlineBeforeOrderByDeadlineAsc(
            Long courseId, AssignmentStatus status, LocalDateTime before);

    /** 统计某课程已发布的作业数量 */
    long countByCourseIdAndStatus(Long courseId, AssignmentStatus status);

    @Query("""
            SELECT a FROM Assignment a
            WHERE a.courseId = :courseId
              AND (:status IS NULL OR a.status = :status)
            ORDER BY CASE WHEN a.deadline IS NULL THEN 1 ELSE 0 END,
                     a.deadline ASC,
                     a.createTime DESC
            """)
    Page<Assignment> findPageByCourseId(Long courseId,
                                         AssignmentStatus status,
                                         Pageable pageable);

    @Query("""
            SELECT a FROM Assignment a
            WHERE a.courseId = :courseId
              AND a.status IN :statuses
            ORDER BY CASE WHEN a.deadline IS NULL THEN 1 ELSE 0 END,
                     a.deadline ASC,
                     a.createTime DESC
            """)
    Page<Assignment> findVisiblePageByCourseId(Long courseId,
                                                List<AssignmentStatus> statuses,
                                                Pageable pageable);
}
