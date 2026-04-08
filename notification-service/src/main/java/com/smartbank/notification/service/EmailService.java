package com.smartbank.notification.service;

import com.smartbank.notification.exception.NotificationException;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String RETRY_INSTANCE = "emailRetry";

    private final JavaMailSender    mailSender;
    private final TemplateEngine    templateEngine;
    private final RetryRegistry     retryRegistry;

    @Value("${notification.mail.from:noreply@smartbank.com}")
    private String fromAddress;

    /**
     * Register retry event listeners once the bean is initialised.
     * These give us observability without cluttering the hot path.
     */
    @PostConstruct
    void registerRetryListeners() {
        retryRegistry.retry(RETRY_INSTANCE).getEventPublisher()
                .onRetry(e -> log.warn("Email retry attempt {} for event: {}",
                        e.getNumberOfRetryAttempts(), e.getLastThrowable().getMessage()))
                .onError(e -> log.error("Email delivery failed after {} attempts: {}",
                        e.getNumberOfRetryAttempts(), e.getLastThrowable().getMessage()))
                .onSuccess(e -> {
                    if (e.getNumberOfRetryAttempts() > 0) {
                        log.info("Email delivered on attempt {}", e.getNumberOfRetryAttempts() + 1);
                    }
                });
    }

    /**
     * Sends an HTML email rendered from a Thymeleaf template.
     *
     * <p>Resilience4j retries this method up to 3 times with exponential backoff
     * (1 s → 2 s → 4 s) on {@link NotificationException}.
     * After exhausting retries the exception propagates to the caller.
     *
     * @param to           recipient email address
     * @param subject      email subject line
     * @param templateName Thymeleaf template name (without extension)
     * @param variables    template model variables
     * @throws NotificationException if the SMTP send fails after all retries
     */
    @Retry(name = RETRY_INSTANCE)
    public void sendEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            String htmlContent = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent to {} — subject: {}", to, subject);

        } catch (MessagingException e) {
            log.error("SMTP error sending to {}: {}", to, e.getMessage());
            throw new NotificationException("SMTP send failed: " + e.getMessage(), e);
        }
    }
}
