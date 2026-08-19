package com.example.hotelmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "auth_refresh_tokens",
    indexes = {
        @Index(name = "idx_auth_refresh_tokens_user_active", columnList = "user_id, revoked_at, expires_at"),
        @Index(name = "idx_auth_refresh_tokens_expires_at", columnList = "expires_at")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "jwt_id", nullable = false, unique = true, length = 36)
    private String jwtId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "rotated_to_jti", length = 36)
    private String rotatedToJwtId;
}
