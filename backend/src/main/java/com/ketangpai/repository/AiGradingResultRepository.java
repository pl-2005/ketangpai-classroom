package com.ketangpai.repository;

import com.ketangpai.model.entity.AiGradingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AI 批阅结果 Repository
 */
@Repository
public interface AiGradingResultRepository extends JpaRepository<AiGradingResult, Long> {

    Optional<AiGradingResult> findBySubmissionId(Long submissionId);
}
