package com.ketangpai.model.entity;

import com.ketangpai.model.enums.CourseStatus;
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

/**
 * 课程表
 */
@Entity
@Table(name = "course")
@SQLRestriction("deleted = 0")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, unique = true, length = 20)
    private String courseCode;

    @Column(length = 255)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('ACTIVE','ARCHIVED')")
    @Builder.Default
    private CourseStatus status = CourseStatus.ACTIVE;

    @Column(nullable = false)
    private Long creatorId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;
}
