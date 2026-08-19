package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.AuthRefreshToken;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.exceptions.AuthException;
import com.example.hotelmanagement.repositories.AuthRefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final AuthRefreshTokenRepository authRefreshTokenRepository;
    private final Clock clock;

    public void storeRefreshToken(String jwtId, User user, Instant expiresAt) {
        AuthRefreshToken refreshToken = AuthRefreshToken.builder()
            .user(user)
            .jwtId(jwtId)
            .expiresAt(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
            .build();
        authRefreshTokenRepository.save(refreshToken);
    }

    public void validateRefreshToken(String jwtId, String userPublicId) {
        AuthRefreshToken refreshToken = findRefreshToken(jwtId);
        boolean belongsToUser = userPublicId.equals(refreshToken.getUser().getPublicId());
        boolean isActive = refreshToken.getRevokedAt() == null && refreshToken.getExpiresAt().isAfter(now());
        if (!belongsToUser || !isActive) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ");
        }
    }

    public void revokeRefreshToken(String jwtId) {
        revokeRefreshToken(jwtId, null);
    }

    public void revokeRefreshToken(String jwtId, String rotatedToJwtId) {
        AuthRefreshToken refreshToken = findRefreshToken(jwtId);
        if (refreshToken.getRevokedAt() == null) {
            refreshToken.setRevokedAt(now());
            refreshToken.setRotatedToJwtId(rotatedToJwtId);
        }
    }

    private AuthRefreshToken findRefreshToken(String jwtId) {
        return authRefreshTokenRepository.findByJwtIdForUpdate(jwtId)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ"));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
