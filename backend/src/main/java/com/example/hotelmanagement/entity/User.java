package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "password_hash", columnDefinition = "TEXT")
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private Integer failedLoginCount = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserRole> userRoles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserSocialAccount> socialAccounts = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<AuthToken> authTokens = new HashSet<>();
}
