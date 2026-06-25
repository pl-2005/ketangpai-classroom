package com.ketangpai.model.entity;

import com.ketangpai.model.enums.GradingBatchTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 批阅批量任务表 — 记录每次批量批阅的进度与结果。
 */
@Entity
@Table(name = "grading_batch_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradingBatchTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long assignmentId;

    @Column(nullable = false)
    private Long teacherId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('PENDING','IN_PROGRESS','COMPLETED','PARTIALLY_FAILED','FAILED')")
    @Builder.Default
    private GradingBatchTaskStatus status = GradingBatchTaskStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer completedCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer failedCount = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();

    @Column
    private LocalDateTime updateTime;
}
