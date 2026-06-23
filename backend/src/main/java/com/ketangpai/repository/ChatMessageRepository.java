package com.ketangpai.repository;

import com.ketangpai.model.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 答疑对话 Repository
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 按会话分页查询对话历史（每页最新在上） */
    Page<ChatMessage> findBySessionIdOrderByCreateTimeDesc(String sessionId, Pageable pageable);

    /** 查询某学生在某课程的所有对话历史（不含会话分组的情况） */
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.userId = :userId AND cm.courseId = :courseId AND cm.sessionId IS NULL ORDER BY cm.createTime ASC")
    List<ChatMessage> findByUserIdAndCourseIdWithoutSession(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /** 查询某学生在某课程的所有会话 ID 列表（去重） */
    @Query("SELECT DISTINCT cm.sessionId FROM ChatMessage cm WHERE cm.userId = :userId AND cm.courseId = :courseId AND cm.sessionId IS NOT NULL ORDER BY cm.sessionId")
    List<String> findDistinctSessionsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /** 删除指定会话的全部消息 */
    void deleteBySessionId(String sessionId);
}
