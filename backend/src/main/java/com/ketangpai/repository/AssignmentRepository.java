package com.ketangpai.repository;

import com.ketangpai.model.entity.Assignment;
import com.ketangpai.model.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
