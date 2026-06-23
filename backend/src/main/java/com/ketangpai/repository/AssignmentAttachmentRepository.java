package com.ketangpai.repository;

import com.ketangpai.model.entity.AssignmentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 作业附件 Repository
 */
@Repository
public interface AssignmentAttachmentRepository extends JpaRepository<AssignmentAttachment, Long> {

    List<AssignmentAttachment> findByAssignmentId(Long assignmentId);

    void deleteByAssignmentId(Long assignmentId);
}
