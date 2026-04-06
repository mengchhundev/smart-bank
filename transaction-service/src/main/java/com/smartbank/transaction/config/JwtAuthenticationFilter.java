package com.smartbank.transaction.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();

            if (claims.getExpiration().before(new Date())) {
                filterChain.doFilter(request, response);
                return;
            }

            String username = claims.getSubject();
            String role     = claims.get("role", String.class);
            Long   userId   = claims.get("userId", Long.class);

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
