package com.ketangpai.repository;

import com.ketangpai.model.entity.Topic;
import com.ketangpai.model.enums.TopicStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 话题 Repository
 */
@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    /** 查询课程话题（置顶优先，然后按时间倒序） */
    List<Topic> findByCourseIdOrderByStatusDescCreateTimeDesc(Long courseId);

    List<Topic> findByAuthorId(Long authorId);

    List<Topic> findByCourseIdAndStatus(Long courseId, TopicStatus status);
}
