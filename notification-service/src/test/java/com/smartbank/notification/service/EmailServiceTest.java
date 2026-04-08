package com.smartbank.notification.service;

import com.smartbank.notification.exception.NotificationException;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender  mailSender;
    @Mock TemplateEngine  templateEngine;
    @Mock RetryRegistry   retryRegistry;
    @Mock MimeMessage     mimeMessage;

    @InjectMocks EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@smartbank.com");

        // RetryRegistry.retry() is called in @PostConstruct — stub it to avoid NPE
        var mockRetry = io.github.resilience4j.retry.Retry.ofDefaults("emailRetry");
        when(retryRegistry.retry("emailRetry")).thenReturn(mockRetry);
        emailService.registerRetryListeners();
    }

    @Test
    void sendEmail_success_sendsMessageViaMailSender() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("transaction-success"), any(Context.class)))
                .thenReturn("<html>Success</html>");

        emailService.sendEmail(
                "user@example.com",
                "Transfer Successful",
                "transaction-success",
                Map.of("referenceNumber", "TXN001", "amount", "500", "currency", "USD",
                       "sourceAccount", "ACC001", "targetAccount", "ACC002",
                       "status", "COMPLETED", "failureReason", "", "timestamp", "2026-04-06T10:00:00")
        );

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_messagingException_throwsNotificationException() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html/>");
        doThrow(new MessagingException("Connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendEmail(
                "user@example.com", "Subject", "transaction-success",
                Map.of("referenceNumber", "TXN001", "amount", "500", "currency", "USD",
                       "sourceAccount", "ACC001", "targetAccount", "ACC002",
                       "status", "FAILED", "failureReason", "", "timestamp", "")
        ))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("SMTP send failed");
    }

    @Test
    void sendEmail_templateProcessed_withCorrectVariables() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("transaction-failed"), any(Context.class)))
                .thenReturn("<html>Failed</html>");

        Map<String, Object> vars = Map.of(
                "referenceNumber", "TXN002",
                "amount", "200",
                "currency", "USD",
                "sourceAccount", "ACC001",
                "targetAccount", "ACC002",
                "status", "FAILED",
                "failureReason", "Insufficient funds",
                "timestamp", "2026-04-06T10:00:00"
        );

        emailService.sendEmail("user@example.com", "Transfer Failed", "transaction-failed", vars);

        verify(templateEngine).process(eq("transaction-failed"), any(Context.class));
        verify(mailSender).send(mimeMessage);
    }
}
