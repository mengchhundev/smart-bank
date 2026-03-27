package com.smartbank.notification.dto;

import com.smartbank.notification.entity.NotificationStatus;
import com.smartbank.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String recipient;
    private String subject;
    private String message;
    private NotificationType type;
    private NotificationStatus status;
    private String referenceNumber;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
