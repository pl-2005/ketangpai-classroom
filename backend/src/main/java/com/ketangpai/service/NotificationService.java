package com.ketangpai.service;

import com.ketangpai.model.entity.Notification;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知管理服务
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Page<Notification> list(Long userId, Pageable pageable) {
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
