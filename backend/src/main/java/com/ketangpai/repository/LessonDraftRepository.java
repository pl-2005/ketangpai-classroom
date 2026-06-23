package com.ketangpai.repository;

import com.ketangpai.model.entity.LessonDraft;
import com.ketangpai.model.enums.DraftType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 备课区 Repository
 */
@Repository
public interface LessonDraftRepository extends JpaRepository<LessonDraft, Long> {

    List<LessonDraft> findByUserIdOrderByUpdateTimeDesc(Long userId);

    List<LessonDraft> findByUserIdAndTypeOrderByUpdateTimeDesc(Long userId, DraftType type);

    long countByUserId(Long userId);
}
