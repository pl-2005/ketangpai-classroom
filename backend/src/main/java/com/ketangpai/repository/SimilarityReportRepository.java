package com.ketangpai.repository;

import com.ketangpai.entity.SimilarityReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 相似度报告 Repository
 */
@Repository
public interface SimilarityReportRepository extends JpaRepository<SimilarityReport, Long> {

    List<SimilarityReport> findByAssignmentIdOrderByGeneratedAtDesc(Long assignmentId);
}
