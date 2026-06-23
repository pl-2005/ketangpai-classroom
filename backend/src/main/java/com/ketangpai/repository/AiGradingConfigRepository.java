package com.ketangpai.repository;

import com.ketangpai.entity.AiGradingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AI 批阅配置 Repository
 */
@Repository
public interface AiGradingConfigRepository extends JpaRepository<AiGradingConfig, Long> {

    Optional<AiGradingConfig> findByAssignmentId(Long assignmentId);
}
