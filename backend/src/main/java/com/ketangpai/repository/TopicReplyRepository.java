package com.ketangpai.repository;

import com.ketangpai.model.entity.TopicReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 话题回复 Repository
 */
@Repository
public interface TopicReplyRepository extends JpaRepository<TopicReply, Long> {

    /** 按物化路径排序，实现楼中楼正确展示顺序 */
    List<TopicReply> findByTopicIdOrderByPathAscCreateTimeAsc(Long topicId);

    List<TopicReply> findByParentId(Long parentId);

    /** 统计话题下的回复数 */
    long countByTopicId(Long topicId);
}
