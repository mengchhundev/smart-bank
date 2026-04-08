package com.smartbank.notification.channel;

import com.smartbank.notification.dto.TransactionEvent;
import com.smartbank.notification.entity.Notification;
import com.smartbank.notification.entity.NotificationStatus;
import com.smartbank.notification.entity.NotificationType;
import com.smartbank.notification.repository.NotificationRepository;
import com.smartbank.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private static final String TEMPLATE_SUCCESS = "transaction-success";
    private static final String TEMPLATE_FAILED  = "transaction-failed";

    private final EmailService           emailService;
    private final NotificationRepository notificationRepository;

    @Value("${notification.mail.default-recipient:customer@smartbank.com}")
    private String defaultRecipient;

    @Override
    public NotificationType getType() {
        return NotificationType.EMAIL;
    }

    @Override
    public void send(TransactionEvent event) {
        String subject  = buildSubject(event);
        String template = event.isSuccess() ? TEMPLATE_SUCCESS : TEMPLATE_FAILED;

        Notification record = notificationRepository.save(Notification.builder()
                .recipient(event.getSourceAccount())
                .subject(subject)
                .message(buildMessage(event))
                .type(NotificationType.EMAIL)
                .referenceNumber(event.getReferenceNumber())
                .status(NotificationStatus.PENDING)
                .build());

        try {
            emailService.sendEmail(defaultRecipient, subject, template, templateVars(event));
            record.setStatus(NotificationStatus.SENT);
            record.setSentAt(LocalDateTime.now());
            log.info("EMAIL sent for reference={}", event.getReferenceNumber());
        } catch (Exception e) {
            record.setStatus(NotificationStatus.FAILED);
            record.setFailureReason(e.getMessage());
            log.error("EMAIL failed for reference={}: {}", event.getReferenceNumber(), e.getMessage());
        }

        notificationRepository.save(record);
    }

    private String buildSubject(TransactionEvent event) {
        return event.isSuccess()
                ? "Transfer Successful — " + event.getReferenceNumber()
                : "Transfer Failed — " + event.getReferenceNumber();
    }

    private String buildMessage(TransactionEvent event) {
        if (event.isSuccess()) {
            return String.format("Transfer of %s %s from %s to %s completed.",
                    event.getAmount(), event.getCurrency(),
                    event.getSourceAccount(), event.getTargetAccount());
        }
        return String.format("Transfer of %s %s from %s to %s failed. Reason: %s",
                event.getAmount(), event.getCurrency(),
                event.getSourceAccount(), event.getTargetAccount(),
                event.getFailureReason() != null ? event.getFailureReason() : "Unknown");
    }

    private Map<String, Object> templateVars(TransactionEvent event) {
        return Map.of(
                "referenceNumber", event.getReferenceNumber(),
                "amount",          event.getAmount().toPlainString(),
                "currency",        event.getCurrency() != null ? event.getCurrency() : "USD",
                "sourceAccount",   event.getSourceAccount(),
                "targetAccount",   event.getTargetAccount(),
                "status",          event.getStatus() != null ? event.getStatus() : "",
                "failureReason",   event.getFailureReason() != null ? event.getFailureReason() : "",
                "timestamp",       event.getTimestamp() != null ? event.getTimestamp().toString() : ""
        );
    }
}
