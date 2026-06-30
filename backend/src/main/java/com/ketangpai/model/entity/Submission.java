package com.ketangpai.model.entity;

import com.ketangpai.model.enums.SubmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Transient;

import java.time.LocalDateTime;

/**
 * 作业提交表
 * <p>
 * 不继承 BaseEntity：无 updateTime 字段，submittedAt / gradedAt 为业务时间。
 */
@Entity
@Table(name = "submission", uniqueConstraints = {
        @UniqueConstraint(name = "uk_assignment_student", columnNames = {"assignment_id", "student_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long assignmentId;

    @Column(nullable = false)
    private Long studentId;

    /** 学生姓名（非持久化，查询时从 User 表填充） */
    @Transient
    private String studentName;

    /** 学生用户名（非持久化，查询时从 User 表填充） */
    @Transient
    private String studentUsername;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('DRAFT','SUBMITTED','GRADED','RETURNED')")
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String teacherComment;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    private LocalDateTime submittedAt;

    private LocalDateTime gradedAt;

    @Column
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();
}
