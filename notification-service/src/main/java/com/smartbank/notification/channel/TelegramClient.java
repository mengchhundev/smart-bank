package com.smartbank.notification.channel;

import com.smartbank.notification.exception.NotificationException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracted into its own bean so that {@code @Retry} AOP proxy works correctly.
 * Self-invocation within {@link TelegramNotificationChannel} would bypass the proxy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.telegram.enabled", havingValue = "true")
public class TelegramClient {

    private static final String API_URL = "https://api.telegram.org/bot{token}/sendMessage";

    private final RestTemplate restTemplate;

    @Retry(name = "telegramRetry")
    public void send(String botToken, String chatId, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id",    chatId);
        body.put("text",       text);
        body.put("parse_mode", "HTML");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class,
                    botToken
            );

            Boolean ok = response.getBody() != null
                    ? (Boolean) response.getBody().get("ok") : Boolean.FALSE;

            if (!Boolean.TRUE.equals(ok)) {
                throw new NotificationException("Telegram API returned ok=false: " + response.getBody());
            }

            log.debug("Telegram message sent to chatId={}", chatId);

        } catch (RestClientException e) {
            throw new NotificationException("Telegram HTTP call failed: " + e.getMessage(), e);
        }
    }
}
