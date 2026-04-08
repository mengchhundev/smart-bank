package com.smartbank.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Rate limiter key resolver configuration.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code X-User-Id} header — set by {@link com.smartbank.gateway.filter.JwtAuthenticationFilter}
 *       after JWT validation. Gives per-user rate limiting.</li>
 *   <li>Client IP address — fallback for requests that bypass JWT (should not happen
 *       on protected routes, but prevents a key-resolution error crashing the filter).</li>
 * </ol>
 *
 * <p>Rate limits are configured per-route in {@code application.yml}:
 * <ul>
 *   <li>account-service:     10 req/s, burst 20</li>
 *   <li>transaction-service:  5 req/s, burst 10  (lower — financial operations)</li>
 *   <li>notification-service: 10 req/s, burst 20</li>
 * </ul>
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            // Fallback to IP — ensures KeyResolver never returns empty
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String ip = remote != null ? remote.getHostString() : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
