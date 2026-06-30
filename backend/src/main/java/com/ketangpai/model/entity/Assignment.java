package com.ketangpai.model.entity;

import com.ketangpai.model.enums.AssignmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Transient;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 作业表
 */
@Entity
@Table(name = "assignment")
@SQLRestriction("deleted = 0")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assignment extends BaseEntity {

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('DRAFT','PUBLISHED','CLOSED')")
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    private LocalDateTime deadline;

    @Column(nullable = false)
    @Builder.Default
    private Integer maxScore = 100;

    @Column(nullable = false)
    @Builder.Default
    private Boolean allowResubmit = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    /** 提交统计（非持久化）— 教师端填充 */
    @Transient
    private Map<String, Object> stats;

    /** 当前用户的提交状态（非持久化）— 学生端填充 */
    @Transient
    private String mySubmissionStatus;
}
