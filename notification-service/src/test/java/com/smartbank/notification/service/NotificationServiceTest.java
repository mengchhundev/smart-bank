package com.smartbank.notification.service;

import com.smartbank.notification.dto.TransactionEvent;
import com.smartbank.notification.entity.Notification;
import com.smartbank.notification.entity.NotificationStatus;
import com.smartbank.notification.exception.NotificationException;
import com.smartbank.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock EmailService           emailService;

    @InjectMocks NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "defaultRecipient", "customer@smartbank.com");

        // Repository save returns the passed entity (simulates JPA behaviour)
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Success event ─────────────────────────────────────────────────────────

    @Test
    void processTransactionEvent_successEvent_savesNotificationAsSent() {
        TransactionEvent event = successEvent();

        notificationService.processTransactionEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        Notification finalSave = captor.getAllValues().get(1);
        assertThat(finalSave.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(finalSave.getSentAt()).isNotNull();
        assertThat(finalSave.getFailureReason()).isNull();
    }

    @Test
    void processTransactionEvent_successEvent_usesSuccessTemplate() {
        TransactionEvent event = successEvent();

        notificationService.processTransactionEvent(event);

        verify(emailService).sendEmail(
                eq("customer@smartbank.com"),
                contains("Transfer Successful"),
                eq("transaction-success"),
                anyMap()
        );
    }

    // ── Failed event ──────────────────────────────────────────────────────────

    @Test
    void processTransactionEvent_failedEvent_usesFailedTemplate() {
        TransactionEvent event = failedEvent();

        notificationService.processTransactionEvent(event);

        verify(emailService).sendEmail(
                eq("customer@smartbank.com"),
                contains("Transfer Failed"),
                eq("transaction-failed"),
                anyMap()
        );
    }

    @Test
    void processTransactionEvent_failedEvent_subjectContainsReferenceNumber() {
        TransactionEvent event = failedEvent();

        notificationService.processTransactionEvent(event);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(anyString(), subjectCaptor.capture(), anyString(), anyMap());
        assertThat(subjectCaptor.getValue()).contains("TXN001");
    }

    // ── Email failure ─────────────────────────────────────────────────────────

    @Test
    void processTransactionEvent_emailFails_savesNotificationAsFailed() {
        doThrow(new NotificationException("SMTP unavailable"))
                .when(emailService).sendEmail(anyString(), anyString(), anyString(), anyMap());

        notificationService.processTransactionEvent(successEvent());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        Notification finalSave = captor.getAllValues().get(1);
        assertThat(finalSave.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(finalSave.getFailureReason()).contains("SMTP unavailable");
        assertThat(finalSave.getSentAt()).isNull();
    }

    @Test
    void processTransactionEvent_emailFails_doesNotRethrow() {
        doThrow(new NotificationException("SMTP unavailable"))
                .when(emailService).sendEmail(anyString(), anyString(), anyString(), anyMap());

        // Must not throw — failure is absorbed and logged
        notificationService.processTransactionEvent(successEvent());
    }

    // ── Template variables ────────────────────────────────────────────────────

    @Test
    void processTransactionEvent_templateVariables_containRequiredKeys() {
        TransactionEvent event = successEvent();
        ArgumentCaptor<Map> varsCaptor = ArgumentCaptor.forClass(Map.class);

        notificationService.processTransactionEvent(event);

        verify(emailService).sendEmail(anyString(), anyString(), anyString(), varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertThat(vars).containsKeys(
                "referenceNumber", "amount", "currency",
                "sourceAccount", "targetAccount", "status", "failureReason", "timestamp"
        );
        assertThat(vars.get("referenceNumber")).isEqualTo("TXN001");
        assertThat(vars.get("amount")).isEqualTo("500");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private TransactionEvent successEvent() {
        return TransactionEvent.builder()
                .eventType("TRANSACTION_SUCCESS")
                .referenceNumber("TXN001")
                .sourceAccount("ACC001")
                .targetAccount("ACC002")
                .amount(new BigDecimal("500"))
                .currency("USD")
                .status("COMPLETED")
                .transactionType("TRANSFER")
                .timestamp(LocalDateTime.now())
                .build();
    }

    private TransactionEvent failedEvent() {
        return TransactionEvent.builder()
                .eventType("TRANSACTION_FAILED")
                .referenceNumber("TXN001")
                .sourceAccount("ACC001")
                .targetAccount("ACC002")
                .amount(new BigDecimal("500"))
                .currency("USD")
                .status("FAILED")
                .transactionType("TRANSFER")
                .failureReason("Insufficient funds")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
