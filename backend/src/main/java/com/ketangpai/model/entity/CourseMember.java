package com.ketangpai.model.entity;

import com.ketangpai.model.enums.CourseMemberRole;
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

import java.time.LocalDateTime;

/**
 * 课程成员表
 * <p>
 * 注意：不继承 BaseEntity，因为此表不需要 updateTime 字段。
 * 归档（isArchived）和退课（deleted）是两种不同的操作，语义独立。
 */
@Entity
@Table(name = "course_member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_user", columnNames = {"course_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('CREATOR','TEACHER','STUDENT')")
    private CourseMemberRole role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isArchived = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();
}
