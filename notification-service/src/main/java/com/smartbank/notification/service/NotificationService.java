package com.smartbank.notification.service;

import com.smartbank.notification.channel.NotificationChannel;
import com.smartbank.notification.dto.NotificationDto;
import com.smartbank.notification.dto.TransactionEvent;
import com.smartbank.notification.entity.Notification;
import com.smartbank.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationChannel> channels;
    private final NotificationRepository    notificationRepository;

    /**
     * Fan-out: delivers the event to every registered {@link NotificationChannel}.
     *
     * <p>Each channel handles its own persistence, retry, and failure isolation.
     * A failure in one channel does NOT abort delivery to the others.
     *
     * <p>To add a new channel, implement {@link NotificationChannel} and register
     * it as a Spring {@code @Component} — no changes needed here.
     */
    public void processTransactionEvent(TransactionEvent event) {
        log.info("Fan-out {} event for reference={} to {} channel(s)",
                event.getEventType(), event.getReferenceNumber(), channels.size());

        for (NotificationChannel channel : channels) {
            try {
                channel.send(event);
            } catch (Exception e) {
                // Channel failure is isolated — other channels still run
                log.error("Channel {} threw unexpected exception for reference={}: {}",
                        channel.getType(), event.getReferenceNumber(), e.getMessage(), e);
            }
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    public List<NotificationDto> getByRecipient(String recipient) {
        return notificationRepository.findByRecipient(recipient).stream()
                .map(this::toDto)
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
