package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.AuthProperties;
import com.example.hotelmanagement.entity.AuthToken;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.AuthTokenType;
import com.example.hotelmanagement.exceptions.AuthException;
import com.example.hotelmanagement.repositories.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final AuthTokenRepository authTokenRepository;
    private final AuthProperties authProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public IssuedAuthToken createToken(User user, AuthTokenType tokenType, String requestedIp) {
        OffsetDateTime issuedAt = now();
        String rawToken = generateTokenValue();
        AuthToken authToken = AuthToken.builder()
            .user(user)
            .tokenType(tokenType)
            .tokenHash(hashToken(rawToken))
            .expiresAt(issuedAt.plus(resolveTokenTtl(tokenType)))
            .requestedIp(normalizeBlank(requestedIp))
            .build();

        authTokenRepository.expireActiveTokens(user, tokenType, issuedAt);
        AuthToken savedToken = authTokenRepository.save(authToken);
        return new IssuedAuthToken(rawToken, savedToken.getExpiresAt());
    }

    @Transactional
    public AuthToken validateToken(String rawToken, AuthTokenType expectedType) {
        String tokenHash = hashToken(requireTokenValue(rawToken));
        AuthToken authToken = authTokenRepository.findByTokenHashForUpdate(tokenHash)
            .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "Authentication token is invalid"));
        ensureTokenMatchesExpectedType(authToken, expectedType);
        ensureTokenIsActive(authToken);
        return authToken;
    }

    @Transactional
    public AuthToken consumeToken(String rawToken, AuthTokenType expectedType) {
        AuthToken authToken = validateToken(rawToken, expectedType);
        authToken.setUsedAt(now());
        return authToken;
    }

    /**
     * Find token by raw value, returns Optional.
     * Does not throw exceptions - returns empty if token is invalid.
     */
    @Transactional(readOnly = true)
    public Optional<AuthToken> findTokenForVerification(String rawToken) {
        try {
            String tokenHash = hashToken(requireTokenValue(rawToken));
            return authTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> token.getTokenType() == AuthTokenType.EMAIL_VERIFICATION);
        } catch (AuthException e) {
            return Optional.empty();
        }
    }

    private String generateTokenValue() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            log.error("Unable to hash auth token because SHA-256 is unavailable", exception);
            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot process authentication token");
        }
    }

    private String requireTokenValue(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Authentication token is invalid");
        }
        String tokenValue = rawToken.trim();
        if (!TOKEN_PATTERN.matcher(tokenValue).matches()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Authentication token is invalid");
        }
        return tokenValue;
    }

    private void ensureTokenMatchesExpectedType(AuthToken authToken, AuthTokenType expectedType) {
        if (authToken.getTokenType() != expectedType) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Authentication token is invalid");
        }
    }

    private void ensureTokenIsActive(AuthToken authToken) {
        if (authToken.getUsedAt() != null || !authToken.getExpiresAt().isAfter(now())) {
            throw new AuthException(HttpStatus.GONE, "Authentication token is expired or already used");
        }
    }

    private Duration resolveTokenTtl(AuthTokenType tokenType) {
        return switch (tokenType) {
            case EMAIL_VERIFICATION -> authProperties.emailVerificationTokenTtl();
            case PASSWORD_RESET -> authProperties.passwordResetTokenTtl();
            case EMAIL_CHANGE -> authProperties.emailVerificationTokenTtl();
        };
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record IssuedAuthToken(String value, OffsetDateTime expiresAt) {
    }
}
