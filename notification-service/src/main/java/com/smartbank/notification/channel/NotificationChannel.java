package com.smartbank.notification.channel;

import com.smartbank.notification.dto.TransactionEvent;
import com.smartbank.notification.entity.NotificationType;

/**
 * Strategy interface for notification delivery channels.
 *
 * <p>Each implementation handles its own:
 * <ul>
 *   <li>Recipient resolution</li>
 *   <li>Message formatting</li>
 *   <li>Retry logic</li>
 *   <li>Persistence of the {@code Notification} log</li>
 * </ul>
 *
 * To add a new channel (SMS, WhatsApp, etc.), implement this interface
 * and register it as a Spring bean — {@link com.smartbank.notification.service.NotificationService}
 * will auto-discover it with no further changes.
 */
public interface NotificationChannel {

    /** Channel type — used to tag the {@code Notification} log row. */
    NotificationType getType();

    /**
     * Send a notification for the given transaction event.
     *
     * @param event the saga event from transaction-service
     */
    void send(TransactionEvent event);
}
