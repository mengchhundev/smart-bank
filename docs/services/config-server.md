# Config Server

**Port:** 8888  
**Module:** `config-server`  
**Package:** `com.smartbank.config`

## Purpose

Centralized externalized configuration using Spring Cloud Config Server. All other services bootstrap from here before connecting to Eureka.

## Key Details

- Profile: `native` — reads config files from classpath (`configurations/`)
- Security: HTTP Basic (`config / config123`)
- Registers itself with Eureka after startup
- Zipkin tracing enabled

## Startup Dependency

Must start **first** — all other services depend on it for their configuration.

## Config File Location

```
config-server/src/main/resources/configurations/application.yml
```

Add service-specific overrides as `{service-name}.yml` in the same directory.

## Dependencies

- `spring-cloud-config-server`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-security`
