package com.ketangpai.repository;

import com.ketangpai.model.entity.Notification;
import com.ketangpai.model.enums.NotificationType;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知 Repository
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 分页查询用户通知（未删除的） */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.deleted = false ORDER BY n.createTime DESC")
    Page<Notification> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /** 查询未读通知数 */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.isRead = false AND n.deleted = false")
    long countUnreadByUserId(@Param("userId") Long userId);

    /** 查询指定类型的通知 */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.type = :type AND n.deleted = false ORDER BY n.createTime DESC")
    List<Notification> findByUserIdAndType(@Param("userId") Long userId, @Param("type") NotificationType type);

    /** 分页查询指定类型的通知 */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.type = :type AND n.deleted = false ORDER BY n.createTime DESC")
    Page<Notification> findByUserIdAndType(@Param("userId") Long userId, @Param("type") NotificationType type, Pageable pageable);

    /** 批量标记通知为已读 */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);

    /** 软删除通知 */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.deleted = true WHERE n.id = :id AND n.userId = :userId")
    int softDeleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
