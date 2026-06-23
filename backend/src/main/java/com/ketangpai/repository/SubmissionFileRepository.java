package com.ketangpai.repository;

import com.ketangpai.model.entity.SubmissionFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 提交文件 Repository
 */
@Repository
public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, Long> {

    List<SubmissionFile> findBySubmissionId(Long submissionId);

    void deleteBySubmissionId(Long submissionId);
}
