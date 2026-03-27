package com.smartbank.notification.repository;

import com.smartbank.notification.entity.Notification;
import com.smartbank.notification.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipient(String recipient);
    List<Notification> findByStatus(NotificationStatus status);
    List<Notification> findByReferenceNumber(String referenceNumber);
}
