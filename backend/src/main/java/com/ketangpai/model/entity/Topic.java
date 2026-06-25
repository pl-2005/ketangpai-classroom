package com.ketangpai.model.entity;

import com.ketangpai.model.enums.TopicStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * 话题表
 */
@Entity
@Table(name = "topic")
@SQLRestriction("deleted = 0")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Topic extends BaseEntity {

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('NORMAL','PINNED','LOCKED')")
    @Builder.Default
    private TopicStatus status = TopicStatus.NORMAL;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isAnonymous = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean discussionEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    /** 瞬态字段：作者姓名（不持久化），由 Service 层填充 */
    @Transient
    private String authorName;
}
