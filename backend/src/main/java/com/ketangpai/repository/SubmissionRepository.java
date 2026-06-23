package com.ketangpai.repository;

import com.ketangpai.entity.Submission;
import com.ketangpai.model.enums.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 作业提交 Repository
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    List<Submission> findByAssignmentId(Long assignmentId);

    List<Submission> findByStudentId(Long studentId);

    /** 查询某作业的所有已提交记录（SUBMITTED / GRADED / RETURNED） */
    @Query("SELECT s FROM Submission s WHERE s.assignmentId = :assignmentId AND s.status IN ('SUBMITTED', 'GRADED', 'RETURNED')")
    List<Submission> findSubmittedByAssignmentId(@Param("assignmentId") Long assignmentId);

    /** 统计某作业的提交状态数量 */
    long countByAssignmentIdAndStatus(Long assignmentId, SubmissionStatus status);

    /** 查询某学生待提交的作业数 */
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.studentId = :studentId AND s.status IN ('DRAFT', 'RETURNED')")
    long countPendingByStudentId(@Param("studentId") Long studentId);
}
