package com.smartbank.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.api-gateway-url:http://localhost:8080}")
    private String gatewayUrl;

    @Bean
    public OpenAPI smartBankAuthOpenAPI() {
        final String securitySchemeName = "Bearer JWT";

        return new OpenAPI()
                .info(new Info()
                        .title("SmartBank Auth Service API")
                        .description("Authentication & Authorization — Register, Login, JWT, Refresh, Logout")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SmartBank Team")
                                .email("dev@smartbank.com")))
                .servers(List.of(
                        new Server().url(gatewayUrl).description("SmartBank API Gateway")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your access_token here")));
    }
}
