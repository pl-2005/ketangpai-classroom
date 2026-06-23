package com.ketangpai.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 相似度报告表
 * <p>
 * 每次教师触发相似度分析，生成一份报告 + 多对相似对。
 */
@Entity
@Table(name = "similarity_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long assignmentId;

    @Column(nullable = false)
    private Integer totalSubmissions;

    @Column(nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal threshold = new BigDecimal("0.80");

    @Column
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
}
