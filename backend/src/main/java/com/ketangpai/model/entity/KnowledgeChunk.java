package com.ketangpai.model.entity;

import com.ketangpai.model.enums.SourceType;
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

import java.time.LocalDateTime;

/**
 * 知识库文档块表（RAG 架构）
 * <p>
 * 存储课程资料切分后的文本块，通过 qdrantPointId 关联 Qdrant 向量数据库。
 */
@Entity
@Table(name = "knowledge_chunk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('MATERIAL','ASSIGNMENT','TOPIC')")
    private SourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(length = 200)
    private String sourceName;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 100)
    private String qdrantPointId;

    @Column
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();
}
