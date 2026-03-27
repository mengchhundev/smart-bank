package com.smartbank.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/api/v1/auth/**")
                        .uri("lb://auth-service"))
                .route("account-service", r -> r
                        .path("/api/v1/accounts/**")
                        .uri("lb://account-service"))
                .route("transaction-service", r -> r
                        .path("/api/v1/transactions/**")
                        .uri("lb://transaction-service"))
                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**")
                        .uri("lb://notification-service"))
                .build();
    }
}
