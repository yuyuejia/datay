package com.data.datafusion.repository;

import com.data.datafusion.domain.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    Page<Notification> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFlagFalse(Long userId);

    List<Notification> findByUserIdAndReadFlagFalse(Long userId);

    Optional<Notification> findByIdAndUserId(String id, Long userId);

    List<Notification> findByEventId(String eventId);

    boolean existsByEventIdAndUserId(String eventId, Long userId);
}
