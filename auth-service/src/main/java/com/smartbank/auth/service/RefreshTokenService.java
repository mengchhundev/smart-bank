package com.smartbank.auth.service;

import com.smartbank.auth.entity.RefreshToken;
import com.smartbank.auth.entity.User;
import com.smartbank.auth.exception.InvalidTokenException;
import com.smartbank.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Revoke any existing active tokens for this user
        refreshTokenRepository.revokeAllByUserId(user.getId());

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenExpiration))
                .build();

        log.debug("Refresh token created for user: {}", user.getUsername());
        return refreshTokenRepository.save(token);
    }

    @Transactional(readOnly = true)
    public RefreshToken verifyRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(token)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found or already revoked"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.save(revokeToken(refreshToken));
            throw new InvalidTokenException("Refresh token expired. Please login again");
        }

        return refreshToken;
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(token)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found or already revoked"));

        refreshTokenRepository.save(revokeToken(refreshToken));
        log.debug("Refresh token revoked for user: {}", refreshToken.getUser().getUsername());
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        int count = refreshTokenRepository.revokeAllByUserId(userId);
        log.debug("Revoked {} refresh tokens for userId: {}", count, userId);
    }

    private RefreshToken revokeToken(RefreshToken token) {
        token.setRevoked(true);
        return token;
    }
}
