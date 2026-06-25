package com.ketangpai.service;

import com.ketangpai.model.entity.Notification;
import com.ketangpai.model.enums.NotificationType;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 通知管理服务
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** 创建通知 */
    @Transactional
    public Notification create(Long userId, Long courseId, NotificationType type,
                                String title, String content, Long relatedId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .courseId(courseId)
                .type(type)
                .title(title)
                .content(content)
                .relatedId(relatedId)
                .build();
        return notificationRepository.save(notification);
    }

    /** 批量创建通知（同一通知发送给多个用户） */
    @Transactional
    public void createBatch(List<Long> userIds, Long courseId, NotificationType type,
                             String title, String content, Long relatedId) {
        List<Notification> notifications = userIds.stream()
                .map(userId -> Notification.builder()
                        .userId(userId)
                        .courseId(courseId)
                        .type(type)
                        .title(title)
                        .content(content)
                        .relatedId(relatedId)
                        .build())
                .toList();
        notificationRepository.saveAll(notifications);
    }

    public Page<Notification> list(Long userId, NotificationType type, Pageable pageable) {
        if (type != null) {
            return notificationRepository.findByUserIdAndType(userId, type, pageable);
        }
        return notificationRepository.findByUserId(userId, pageable);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(404, "通知不存在"));
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该通知");
        }
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void delete(Long notificationId, Long userId) {
        int affected = notificationRepository.softDeleteByIdAndUserId(notificationId, userId);
        if (affected == 0) {
            throw new BusinessException(404, "通知不存在或无权操作");
        }
    }
}
