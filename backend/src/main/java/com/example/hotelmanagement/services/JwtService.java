package com.example.hotelmanagement.services;

import com.example.hotelmanagement.config.JwtProperties;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.exceptions.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        byte[] secretBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public GeneratedToken generateAccessToken(
        User user,
        Collection<String> roles,
        Collection<String> permissions
    ) {
        return generateToken(
            user,
            ACCESS_TOKEN_TYPE,
            jwtProperties.accessTokenTtl(),
            Map.of("roles", roles, "permissions", permissions)
        );
    }

    public GeneratedToken generateRefreshToken(User user) {
        return generateToken(user, REFRESH_TOKEN_TYPE, jwtProperties.refreshTokenTtl(), Map.of());
    }

    public Claims parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        requireTokenType(claims, ACCESS_TOKEN_TYPE);
        return claims;
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        requireTokenType(claims, REFRESH_TOKEN_TYPE);
        return claims;
    }

    public long getAccessTokenTtlSeconds() {
        return jwtProperties.accessTokenTtl().toSeconds();
    }

    public long getRefreshTokenTtlSeconds() {
        return jwtProperties.refreshTokenTtl().toSeconds();
    }

    private GeneratedToken generateToken(
        User user,
        String tokenType,
        java.time.Duration ttl,
        Map<String, Object> extraClaims
    ) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(ttl);
        String jwtId = UUID.randomUUID().toString();
        String value = Jwts.builder()
            .subject(user.getPublicId())
            .id(jwtId)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .claim("uid", user.getId())
            .claim("email", user.getEmail())
            .claim(TOKEN_TYPE_CLAIM, tokenType)
            .claims(extraClaims)
            .signWith(signingKey)
            .compact();
        return new GeneratedToken(value, jwtId, expiresAt);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Token không hợp lệ");
        }
    }

    private void requireTokenType(Claims claims, String expectedType) {
        if (!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Token không hợp lệ");
        }
    }

    public record GeneratedToken(String value, String jwtId, Instant expiresAt) {
    }
}
