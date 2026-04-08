package com.smartbank.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that logs every inbound request and its response.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so it wraps the entire
 * filter chain and captures total gateway latency.
 *
 * <p>Each request is assigned a short {@code X-Request-Id} header propagated
 * to downstream services for distributed tracing correlation.
 *
 * <p>Log format:
 * <pre>
 * [REQ ] [abc12345] GET  /api/transactions/transfer  from 127.0.0.1
 * [RESP] [abc12345] GET  /api/transactions/transfer  → 201 CREATED  (143ms)
 * </pre>
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request  = exchange.getRequest();
        String            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String            method   = request.getMethod().name();
        String            path     = request.getURI().getPath();
        String            remote   = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getHostString() : "unknown";

        log.info("[REQ ] [{}] {} {} from {}", requestId, method, path, remote);
        long start = System.currentTimeMillis();

        return chain.filter(
                exchange.mutate()
                        .request(r -> r.header("X-Request-Id", requestId))
                        .build()
        ).doFinally(signal -> {
            long duration = System.currentTimeMillis() - start;
            log.info("[RESP] [{}] {} {} → {} ({}ms)",
                    requestId, method, path,
                    exchange.getResponse().getStatusCode(),
                    duration);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
