package com.ketangpai.repository;

import com.ketangpai.model.entity.TempFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 临时文件 Repository
 */
public interface TempFileRepository extends JpaRepository<TempFile, Long> {

    /**
     * 查找过期且未关联的临时文件（createTime 早于 cutoff 且 associated=false）。
     */
    List<TempFile> findByAssociatedFalseAndCreateTimeBefore(LocalDateTime cutoff);

    /**
     * 删除指定 ID 列表中 associated=false 的记录。
     */
    @Modifying
    @Query("DELETE FROM TempFile t WHERE t.id IN :ids AND t.associated = false")
    int deleteByIdInAndAssociatedFalse(@Param("ids") List<Long> ids);
}
