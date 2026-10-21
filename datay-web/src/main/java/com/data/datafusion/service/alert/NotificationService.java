package com.data.datafusion.service.alert;

import com.data.datafusion.domain.Notification;
import com.data.datafusion.repository.NotificationRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.dto.NotificationDTO;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 站内信服务，所有查询都限定在当前登录用户。
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotificationDTO> findAll(Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreateTimeDesc(currentUserId(), pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserIdAndReadFlagFalse(currentUserId());
    }

    public void markRead(String id) {
        notificationRepository
            .findByIdAndUserId(id, currentUserId())
            .ifPresent(notification -> {
                if (!Boolean.TRUE.equals(notification.getReadFlag())) {
                    notification.setReadFlag(Boolean.TRUE);
                    notification.setReadTime(ZonedDateTime.now());
                    notificationRepository.save(notification);
                }
            });
    }

    public void markAllRead() {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFlagFalse(currentUserId());
        ZonedDateTime now = ZonedDateTime.now();
        for (Notification notification : unread) {
            notification.setReadFlag(Boolean.TRUE);
            notification.setReadTime(now);
        }
        notificationRepository.saveAll(unread);
    }

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new IllegalStateException("未登录"));
    }

    private NotificationDTO toDto(Notification notification) {
        return new NotificationDTO(
            notification.getId(),
            notification.getEventId(),
            notification.getTitle(),
            notification.getContent(),
            notification.getReadFlag(),
            notification.getCreateTime(),
            notification.getReadTime()
        );
    }
}
