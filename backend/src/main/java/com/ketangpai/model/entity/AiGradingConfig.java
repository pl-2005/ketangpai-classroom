package com.ketangpai.model.entity;

import com.ketangpai.model.enums.GradingStyle;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * AI 批阅配置表（与作业一对一）
 */
@Entity
@Table(name = "ai_grading_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiGradingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long assignmentId;

    @Column(columnDefinition = "TEXT")
    private String promptTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private String rubricJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('STRICT','BALANCED','ENCOURAGING','CONCISE')")
    @Builder.Default
    private GradingStyle gradingStyle = GradingStyle.BALANCED;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();

    @Column
    private LocalDateTime updateTime;
}
