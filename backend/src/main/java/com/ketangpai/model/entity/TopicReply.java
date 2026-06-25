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

import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 话题回复表
 * <p>
 * 不继承 BaseEntity：无 updateTime 字段。
 * path 为物化路径（如 /1/3/5），用于高效楼中楼排序。
 */
@Entity
@Table(name = "topic_reply")
@SQLRestriction("deleted = 0")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long topicId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isAnonymous = false;

    private Long parentId;

    @Column(length = 500)
    private String path;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();
}
