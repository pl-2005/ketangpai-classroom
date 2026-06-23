package com.ketangpai.repository;

import com.ketangpai.entity.KnowledgeChunk;
import com.ketangpai.model.enums.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识库文档块 Repository
 */
@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByCourseId(Long courseId);

    List<KnowledgeChunk> findBySourceTypeAndSourceId(SourceType sourceType, Long sourceId);

    /** 查询某课程下指定来源类型的全部文档块 */
    List<KnowledgeChunk> findByCourseIdAndSourceType(Long courseId, SourceType sourceType);

    /** 删除某来源的全部文档块（重新索引时使用） */
    void deleteBySourceTypeAndSourceId(SourceType sourceType, Long sourceId);
}
