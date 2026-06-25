package com.ketangpai.repository;

import com.ketangpai.model.entity.GradingBatchTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * AI 批阅批量任务仓库
 */
public interface GradingBatchTaskRepository extends JpaRepository<GradingBatchTask, Long> {

    /** 按作业 ID 查询任务列表，按创建时间倒序 */
    List<GradingBatchTask> findByAssignmentIdOrderByCreateTimeDesc(Long assignmentId);

    /** 按 ID 查询单个任务 */
    Optional<GradingBatchTask> findById(Long id);
}
