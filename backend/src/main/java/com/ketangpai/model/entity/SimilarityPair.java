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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * 相似度对表
 * <p>
 * 存储两份提交之间的语义相似度，支持按单份提交检索（跨作业批次对比）。
 */
@Entity
@Table(name = "similarity_pair")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarityPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reportId;

    @Column(nullable = false, name = "submission_a_id")
    private Long submissionAId;

    @Column(nullable = false, name = "submission_b_id")
    private Long submissionBId;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal similarityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private String highlightedSegments;
}
