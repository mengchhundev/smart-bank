package com.smartbank.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Global filter that validates the JWT on every request before routing.
 *
 * <p>Open endpoints (auth, swagger, actuator, fallback) bypass validation.
 * On success, the filter enriches the downstream request with:
 * <ul>
 *   <li>{@code X-User-Id}    — JWT subject (userId)</li>
 *   <li>{@code X-User-Roles} — comma-separated roles claim</li>
 * </ul>
 * These headers are consumed by downstream services for authorisation decisions.
 *
 * <p>Order -1 ensures this runs after {@link LoggingFilter} (HIGHEST_PRECEDENCE)
 * but before gateway route filters (rate limiter, circuit breaker).
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final List<String> OPEN_PATHS = List.of(
            "/api/auth/",
            "/actuator",
            "/fallback",
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isOpenPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return rejectWith(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        try {
            Claims claims = parseToken(authHeader.substring(7));

            ServerWebExchange enriched = exchange.mutate()
                    .request(r -> r
                            .header("X-User-Id",    claims.getSubject())
                            .header("X-User-Roles", claims.get("roles", String.class) != null
                                    ? claims.get("roles", String.class) : ""))
                    .build();

            log.debug("JWT validated — userId={} path={}", claims.getSubject(), path);
            return chain.filter(enriched);

        } catch (ExpiredJwtException e) {
            log.warn("JWT expired for path={}: {}", path, e.getMessage());
            return rejectWith(exchange, HttpStatus.UNAUTHORIZED, "Token has expired");
        } catch (SignatureException | MalformedJwtException e) {
            log.warn("JWT invalid for path={}: {}", path, e.getMessage());
            return rejectWith(exchange, HttpStatus.UNAUTHORIZED, "Invalid token");
        } catch (Exception e) {
            log.error("JWT validation error for path={}: {}", path, e.getMessage());
            return rejectWith(exchange, HttpStatus.UNAUTHORIZED, "Token validation failed");
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isOpenPath(String path) {
        return OPEN_PATHS.stream().anyMatch(path::startsWith);
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> rejectWith(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"status":%d,"error":"%s","message":"%s"}"""
                .formatted(status.value(), status.getReasonPhrase(), message);

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
