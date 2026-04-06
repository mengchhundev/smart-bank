# Auth Service — Code Review & Workflow Analysis

## Table of Contents
1. [Security Infrastructure](#1-security-infrastructure)
2. [Workflow: Register](#2-workflow-register)
3. [Workflow: Login](#3-workflow-login)
4. [Workflow: Refresh Token](#4-workflow-refresh-token)
5. [Workflow: Logout](#5-workflow-logout)
6. [Workflow: Validate Token](#6-workflow-validate-token)
7. [JwtService — Token Mechanics](#7-jwtservice--token-mechanics)
8. [Issues to Revisit](#8-issues-to-revisit)

---

## 1. Security Infrastructure

### SecurityConfig — The Rule Book

```java
// SecurityConfig.java:38-52
.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.POST,
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh").permitAll()
    .anyRequest().authenticated()
)
.authenticationProvider(authenticationProvider())
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

**Why `DaoAuthenticationProvider` explicitly?**
`DaoAuthenticationProvider` wires `CustomUserDetailsService` + `BCryptPasswordEncoder` together. Without this explicit bean, Spring Boot auto-configures a provider using the default `UserDetailsService` — but since you have a custom one, being explicit avoids ambiguity and makes the wiring readable.

**Why `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`?**
The JWT filter must run _before_ Spring's form-login filter. If a valid JWT arrives, the security context is populated before Spring tries to authenticate via username/password — preventing unnecessary authentication attempts.

> **Issue:** No `authenticationEntryPoint` is configured. Unauthenticated requests to `/api/auth/validate` return `403 Forbidden` instead of the correct `401 Unauthorized`. Same gap that was fixed in account-service.

---

### JwtAuthenticationFilter — Token Extraction Layer

```java
// JwtAuthenticationFilter.java:47-56
if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
    if (jwtService.isTokenValid(jwt, userDetails)) {
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
```

**Why check `getAuthentication() == null`?**
Guards against processing a token twice if the filter somehow runs again within the same request thread — defensive programming against double-authentication.

**Why `loadUserByUsername()` (a DB hit) on every request?**
To catch the case where a user was disabled _between_ token issuance and this request. The JWT is self-contained but doesn't know the user was disabled 5 minutes ago — only the DB does. The tradeoff: every protected request hits the DB.

> **Performance note:** For the `/validate` endpoint, this results in **2 DB hits per call** — once in the filter, once in the controller. Since `account-service` calls `/validate` on every internal request, this is the highest-traffic query path in the service.

**Why `shouldNotFilter()`?**
Skips JWT parsing for public paths. `SecurityConfig` already permits them, but `shouldNotFilter` prevents even attempting to extract/parse a token on paths that don't need it — avoids unnecessary parsing overhead.

---

### CustomUserDetailsService

```java
// CustomUserDetailsService.java:20-31
return new org.springframework.security.core.userdetails.User(
    user.getUsername(),
    user.getPassword(),
    user.isEnabled(),
    true, true, true,       // accountNonExpired, credentialsNonExpired, accountNonLocked
    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
);
```

**Why the four boolean flags?**
Spring Security's `User` constructor takes `(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities)`. The three `true` values mean: account never expires, credentials never expire, account is never locked. Only `enabled` maps to an actual DB field (`users.enabled`). The other flags are hardcoded because the domain doesn't yet model account expiry or lockout — a future feature would replace those `true` values with DB-driven fields.

**Why `"ROLE_" + user.getRole().name()`?**
Spring Security's `hasRole("ADMIN")` check internally prefixes with `ROLE_`. The authority must be stored as `ROLE_ADMIN` for the check to work. Adding the prefix here in one place is simpler than using `hasAuthority("ROLE_ADMIN")` everywhere in security rules.

---

## 2. Workflow: Register

```
POST /api/auth/register
```

```
Client Request
    │
    ▼ @Valid — validates RegisterRequest fields
    │
    ▼ AuthService.register()
      1. existsByUsername()     — checks uniqueness → 409 if taken
      2. existsByEmail()        — checks uniqueness → 409 if taken
      3. BCrypt.encode(password, cost=12)
      4. userRepository.save() — persists with role=CUSTOMER
      5. buildAuthResponse()   — issues token pair immediately
    │
    ▼ 201 Created — AuthResponse (access_token + refresh_token)
```

**Why issue tokens immediately on registration?**
Eliminates the extra round-trip of registering then immediately logging in. The user is authenticated the moment they're created — a common UX pattern for mobile/SPA registration flows.

**Why `existsByUsername()` + `existsByEmail()` separately instead of relying on DB constraint + catching `DataIntegrityViolationException`?**
Gives a specific, user-friendly error message per field ("Username already taken" vs "Email already registered"). Catching `DataIntegrityViolationException` would give a generic database error that is hard to map cleanly to a human-readable response.

**Why `role = Role.CUSTOMER` hardcoded?**
ADMIN accounts must never be self-provisioned via a public endpoint. Admin creation must happen out-of-band (directly in DB or via a separate admin-only endpoint that's not yet implemented).

**Why BCrypt cost=12?**
Cost 12 is the current industry recommendation: strong enough to be resistant to GPU brute-force (~250ms per hash on modern hardware), not so high that it degrades registration/login UX. Cost 10 is too fast; cost 14+ is perceptibly slow.

---

## 3. Workflow: Login

```
POST /api/auth/login
```

```
Client Request
    │
    ▼ @Valid — validates LoginRequest fields
    │
    ▼ AuthService.login()
      1. authenticationManager.authenticate(UsernamePasswordAuthenticationToken)
         └─► DaoAuthenticationProvider
               ├─► CustomUserDetailsService.loadUserByUsername()  [DB hit]
               ├─► BCrypt.matches(rawPassword, storedHash)
               └─► throws BadCredentialsException / DisabledException if fails
      2. userRepository.findByUsername()  [DB hit — see note below]
      3. buildAuthResponse() → issue token pair
    │
    ▼ 200 OK — AuthResponse
```

**Why delegate to `authenticationManager` instead of manually comparing passwords?**
`AuthenticationManager` handles `BadCredentialsException`, `DisabledException`, `LockedException` etc. automatically — all error cases are covered without manual checks. Manual password comparison would miss `user.isEnabled()` and future lockout logic.

**Why does `GlobalExceptionHandler` return a generic message for `BadCredentialsException`?**

```java
// GlobalExceptionHandler.java:37
return buildError(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
```

Never confirm which field was wrong. Returning "Username not found" vs "Wrong password" as separate messages gives an attacker a user enumeration oracle — they can determine which usernames exist in the system.

> **Bug — `AuthService.java:62`:**
> ```java
> User user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow();
> ```
> `.orElseThrow()` without a supplier throws a bare `NoSuchElementException`, which the `GlobalExceptionHandler` has no specific handler for — falls through to the generic 500. It should be:
> ```java
> .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()))
> ```
> In practice this almost never throws because the user was just loaded by `DaoAuthenticationProvider` milliseconds earlier, but it's a latent 500 waiting to happen during a race condition (e.g. user deleted between authentication and this line).

---

## 4. Workflow: Refresh Token

```
POST /api/auth/refresh
```

```
Client Request
    │
    ▼ @Valid — validates RefreshTokenRequest
    │
    ▼ AuthService.refresh()
      1. RefreshTokenService.verifyRefreshToken(token)
         ├─► findByTokenAndRevokedFalse()  [single query: exists + not-revoked]
         ├─► isExpired() check
         │     └─ if expired: revoke it → save → throw InvalidTokenException (401)
         └─► return valid RefreshToken
      2. refreshToken.getUser()  [LAZY load — additional DB hit]
      3. buildAuthResponse()
         ├─► RefreshTokenService.createRefreshToken()
         │     └─► revokeAllByUserId()   [bulk UPDATE — revokes all existing tokens]
         │     └─► save new RefreshToken
         └─► JwtService.generateAccessToken()
    │
    ▼ 200 OK — AuthResponse (new access_token + new refresh_token)
```

**Why revoke ALL existing tokens on refresh (`revokeAllByUserId`)?**
This implements **refresh token rotation**. Each use of a refresh token invalidates all siblings. If an attacker steals a refresh token and tries to use it _after_ the legitimate user already refreshed, the attacker's token is already revoked. This is the OWASP-recommended pattern for refresh tokens.

**Why issue a brand-new refresh token on every refresh, not just a new access token?**
Same rotation principle. Keeping a long-lived refresh token unchanged means a stolen token stays valid until its 7-day TTL expires — rotation limits that window to the interval between legitimate refreshes.

**Why `findByTokenAndRevokedFalse()` as a single query instead of `findByToken()` then checking `revoked`?**
Reduces data returned from DB and moves the business logic check into the query. Also more explicit in intent — a revoked token and a not-found token are treated identically (both throw `InvalidTokenException`), preventing information leakage about whether a token ever existed.

> **Bug — `RefreshTokenService.java:41-49`:**
> ```java
> @Transactional(readOnly = true)   // ← readOnly!
> public RefreshToken verifyRefreshToken(String token) {
>     ...
>     if (refreshToken.isExpired()) {
>         refreshTokenRepository.save(revokeToken(refreshToken));  // ← WRITE inside readOnly tx
> ```
> `readOnly = true` hints to the connection pool and JDBC driver to use a read-only connection. A `save()` on the expired-token path may silently fail or throw a `TransactionRequiredException` depending on the database driver and connection pool configuration. The annotation should be removed from this method.

---

## 5. Workflow: Logout

```
POST /api/auth/logout
Authorization: Bearer <access_token>   (required by SecurityConfig)
```

```
Client Request
    │
    ▼ JwtAuthenticationFilter — validates access token, populates SecurityContext
    │
    ▼ @Valid — validates LogoutRequest (refresh token in body)
    │
    ▼ AuthService.logout()
      1. RefreshTokenService.revokeRefreshToken(refreshToken)
         ├─► findByTokenAndRevokedFalse()  — validates token exists + not revoked
         └─► set revoked=true, save
    │
    ▼ 204 No Content
```

**Why revoke the refresh token and not the access token?**
Access tokens are stateless — there is no server-side record to revoke. The only way to truly invalidate a stateless JWT access token is a token blocklist, which adds infrastructure complexity. The design accepts a 15-minute residual validity window on access tokens after logout — the refresh token being revoked prevents the session from being extended beyond the current access token's TTL.

**Why does logout require both an access token (header) AND a refresh token (body)?**
The `SecurityConfig` requires `anyRequest().authenticated()`, so a valid JWT in `Authorization` is enforced by the filter. The refresh token in the body is what actually terminates the long-lived session. Both are required: the access token proves the caller is currently authenticated; the refresh token is what gets revoked.

---

## 6. Workflow: Validate Token

```
GET /api/auth/validate
Authorization: Bearer <access_token>
```

```
Client Request (called by account-service via Feign)
    │
    ▼ JwtAuthenticationFilter
      1. extractUsername(jwt)
      2. loadUserByUsername(username)   [DB hit #1]
      3. isTokenValid() → populate SecurityContext
    │
    ▼ AuthController.validate(Authentication authentication)
      1. authentication.getName()          → username
      2. authentication.getAuthorities()   → strip "ROLE_" prefix → role
      3. userRepository.findByUsername()   [DB hit #2 — to get userId]
      4. Return TokenValidationResponse(userId, username, role)
    │
    ▼ 200 OK — TokenValidationResponse
```

**Why does the controller hit the DB a second time?**
Spring Security's `UserDetails` carries only `username`, `password`, and `authorities` — not `userId`. The controller needs `userId` for the response (account-service uses it to set user ownership). Since the principal in the security context is a generic `UserDetails`, a second DB query is the only way to retrieve `userId`.

**Why does this endpoint exist instead of trusting the gateway's `X-User-Id` header?**
The gateway already validates JWT and injects `X-User-Id` into downstream headers. `account-service` reads this header for its public endpoints. The `/validate` Feign call was added for the internal endpoints (`/api/v1/accounts/**`) as a secondary validation layer. In practice, if all internal traffic is only reachable through the gateway, the Feign call to `/validate` is redundant — the token was already verified at the edge.

> **Performance concern:** Because `account-service` calls `/validate` on every internal request, and `/validate` triggers 2 DB hits, every service-to-service balance operation results in 2 extra queries against `auth_db`. At scale, the better design is to embed `userId` in the JWT claims (it already is as `claims.get("userId")`), decode it in the filter directly, and drop the Feign call entirely.

---

## 7. JwtService — Token Mechanics

```java
// JwtService.java:32-38
return Jwts.builder()
    .claims(extraClaims)                    // sets role + userId — must come FIRST
    .subject(userDetails.getUsername())     // sets sub claim — must come AFTER .claims()
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
    .signWith(signingKey)                   // HMAC-SHA256
    .compact();
```

**Why `.claims(extraClaims)` before `.subject()`?**
In JJWT 0.12.x, `.claims(map)` _replaces_ the entire claims map. If called after `.subject()`, it would overwrite the `sub` claim. Calling it first, then `.subject()` after, ensures `sub` is always set correctly. This is a JJWT 0.12 API quirk — earlier versions used `.addClaims()` which was additive and order-independent.

**Why not put `userId` in the `sub` claim instead of an extra claim?**
`sub` is conventionally the human-readable identity (username), used in logs, audit trails, and error messages. Putting a numeric ID there breaks that convention and makes debugging harder. Keeping `userId` as a separate claim makes both available independently downstream.

**Why HMAC-SHA256 (HS256) and not RS256?**
HS256 uses a shared symmetric key — simpler to configure, no key pair management. The tradeoff: every service that validates tokens must have the same secret. RS256 would allow the private key to stay only in auth-service while other services verify with the public key — better for zero-trust internal networks. For this architecture where the secret is already shared (api-gateway and account-service both have it), HS256 is the pragmatic choice.

---

## 8. Issues to Revisit

| File | Line | Issue | Severity |
|------|------|-------|----------|
| `SecurityConfig.java` | 52 | No `authenticationEntryPoint` — unauthenticated requests to `/validate` return 403 instead of 401 | Medium |
| `AuthService.java` | 62 | `.orElseThrow()` without supplier throws bare `NoSuchElementException` → unhandled 500 | Low |
| `RefreshTokenService.java` | 41 | `@Transactional(readOnly = true)` on a method that writes on the expired-token path | Medium |
| `AuthController.java` | 99 | Double DB hit on `/validate` — filter + controller both query `users` table | Performance |
| `AuthController.java` | 90–108 | `/validate` endpoint is redundant if all traffic goes through the gateway which already injects `X-User-Id` | Architecture |
