package com.smartbank.notification.channel;

import com.smartbank.notification.dto.TransactionEvent;
import com.smartbank.notification.entity.Notification;
import com.smartbank.notification.entity.NotificationStatus;
import com.smartbank.notification.entity.NotificationType;
import com.smartbank.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.telegram.enabled", havingValue = "true")
public class TelegramNotificationChannel implements NotificationChannel {

    private final TelegramClient         telegramClient;
    private final NotificationRepository notificationRepository;

    @Value("${notification.telegram.bot-token}")
    private String botToken;

    @Value("${notification.telegram.default-chat-id}")
    private String defaultChatId;

    @Override
    public NotificationType getType() {
        return NotificationType.TELEGRAM;
    }

    @Override
    public void send(TransactionEvent event) {
        String text = buildMessage(event);

        Notification record = notificationRepository.save(Notification.builder()
                .recipient(event.getSourceAccount())
                .subject(event.isSuccess()
                        ? "Transfer Successful — " + event.getReferenceNumber()
                        : "Transfer Failed — " + event.getReferenceNumber())
                .message(text)
                .type(NotificationType.TELEGRAM)
                .referenceNumber(event.getReferenceNumber())
                .status(NotificationStatus.PENDING)
                .build());

        try {
            // Calls through TelegramClient bean — @Retry AOP proxy applies correctly
            telegramClient.send(botToken, defaultChatId, text);
            record.setStatus(NotificationStatus.SENT);
            record.setSentAt(LocalDateTime.now());
            log.info("TELEGRAM sent for reference={}", event.getReferenceNumber());
        } catch (Exception e) {
            record.setStatus(NotificationStatus.FAILED);
            record.setFailureReason(e.getMessage());
            log.error("TELEGRAM failed for reference={}: {}", event.getReferenceNumber(), e.getMessage());
        }

        notificationRepository.save(record);
    }

    private String buildMessage(TransactionEvent event) {
        if (event.isSuccess()) {
            return String.format(
                    "<b>Transfer Successful</b>\n\n" +
                    "Reference: <code>%s</code>\n" +
                    "Amount: <b>%s %s</b>\n" +
                    "From: %s\n" +
                    "To: %s\n" +
                    "Date: %s",
                    event.getReferenceNumber(),
                    event.getAmount().toPlainString(),
                    event.getCurrency() != null ? event.getCurrency() : "USD",
                    event.getSourceAccount(),
                    event.getTargetAccount(),
                    event.getTimestamp() != null ? event.getTimestamp().toString() : ""
            );
        }
        return String.format(
                "<b>Transfer Failed</b>\n\n" +
                "Reference: <code>%s</code>\n" +
                "Amount: <b>%s %s</b>\n" +
                "From: %s\n" +
                "To: %s\n" +
                "Reason: %s\n" +
                "Date: %s",
                event.getReferenceNumber(),
                event.getAmount().toPlainString(),
                event.getCurrency() != null ? event.getCurrency() : "USD",
                event.getSourceAccount(),
                event.getTargetAccount(),
                event.getFailureReason() != null ? event.getFailureReason() : "Unknown",
                event.getTimestamp() != null ? event.getTimestamp().toString() : ""
        );
    }
}
