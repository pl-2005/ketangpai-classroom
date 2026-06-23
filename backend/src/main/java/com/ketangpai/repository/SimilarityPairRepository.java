package com.ketangpai.repository;

import com.ketangpai.entity.SimilarityPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 相似度对 Repository
 */
@Repository
public interface SimilarityPairRepository extends JpaRepository<SimilarityPair, Long> {

    List<SimilarityPair> findByReportId(Long reportId);

    /** 按相似度降序排列（最相似的对排前面） */
    List<SimilarityPair> findByReportIdOrderBySimilarityScoreDesc(Long reportId);

    /** 查询某份提交参与的所有相似对（跨报告/跨批次对比） */
    @Query("SELECT sp FROM SimilarityPair sp WHERE sp.submissionAId = :submissionId OR sp.submissionBId = :submissionId")
    List<SimilarityPair> findBySubmissionId(@Param("submissionId") Long submissionId);

    /** 查询超过指定阈值的相似对 */
    List<SimilarityPair> findByReportIdAndSimilarityScoreGreaterThanEqualOrderBySimilarityScoreDesc(
            Long reportId, java.math.BigDecimal threshold);
}
