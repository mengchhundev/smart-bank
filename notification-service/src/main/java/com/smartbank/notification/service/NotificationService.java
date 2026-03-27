package com.smartbank.notification.service;

import com.smartbank.notification.dto.NotificationDto;
import com.smartbank.notification.dto.TransactionEvent;
import com.smartbank.notification.entity.Notification;
import com.smartbank.notification.entity.NotificationStatus;
import com.smartbank.notification.entity.NotificationType;
import com.smartbank.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    public void processTransactionEvent(TransactionEvent event) {
        String subject = "Transaction " + event.getStatus() + " — " + event.getReferenceNumber();
        String message = String.format("Transaction %s of %s %s from %s to %s is %s",
                event.getReferenceNumber(),
                event.getAmount(), event.getCurrency(),
                event.getSourceAccount(), event.getTargetAccount(),
                event.getStatus());

        Notification notification = Notification.builder()
                .recipient(event.getSourceAccount())
                .subject(subject)
                .message(message)
                .type(NotificationType.EMAIL)
                .referenceNumber(event.getReferenceNumber())
                .build();

        try {
            emailService.sendEmail(
                    "customer@smartbank.com",
                    subject,
                    "transaction-notification",
                    Map.of(
                            "referenceNumber", event.getReferenceNumber(),
                            "amount", event.getAmount().toString(),
                            "currency", event.getCurrency(),
                            "sourceAccount", event.getSourceAccount(),
                            "targetAccount", event.getTargetAccount(),
                            "status", event.getStatus(),
                            "timestamp", event.getTimestamp().toString()
                    )
            );

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            log.info("Notification sent for transaction: {}", event.getReferenceNumber());
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(e.getMessage());
            log.error("Failed to send notification for: {}", event.getReferenceNumber(), e);
        }

        notificationRepository.save(notification);
    }

    public List<NotificationDto> getByRecipient(String recipient) {
        return notificationRepository.findByRecipient(recipient).stream()
                .map(this::toDto)
                .toList();
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .recipient(n.getRecipient())
                .subject(n.getSubject())
                .message(n.getMessage())
                .type(n.getType())
                .status(n.getStatus())
                .referenceNumber(n.getReferenceNumber())
                .createdAt(n.getCreatedAt())
                .sentAt(n.getSentAt())
                .build();
    }
}
