package com.smartbank.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway bean configuration.
 *
 * <p>All routes and their filters are defined declaratively in {@code application.yml}.
 * This class provides infrastructure beans referenced by the YAML config.
 */
@Configuration
public class GatewayConfig {

    /**
     * Default Redis rate limiter shared across routes.
     *
     * <p>Per-route overrides ({@code replenishRate}, {@code burstCapacity}) are
     * applied in {@code application.yml} — this bean sets the fallback defaults.
     *
     * <ul>
     *   <li>replenishRate  = 10 tokens/second (sustained throughput)</li>
     *   <li>burstCapacity  = 20 tokens        (max burst)</li>
     *   <li>requestedTokens = 1 token/request</li>
     * </ul>
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }
}
