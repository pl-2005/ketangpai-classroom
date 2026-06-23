package com.ketangpai.entity;

import com.ketangpai.model.enums.DraftType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * 备课区表（教师专属）
 * <p>
 * contentJson 存储不同类型草稿的结构化 JSON，发布时反序列化一键导入到对应表。
 */
@Entity
@Table(name = "lesson_draft")
@SQLRestriction("deleted = 0")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonDraft extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('ASSIGNMENT','MATERIAL','TOPIC')")
    private DraftType type;

    @Column(length = 200)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private String contentJson;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;
}
