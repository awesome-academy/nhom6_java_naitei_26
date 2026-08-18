package com.example.hotelmanagement.entity;

import com.example.hotelmanagement.entity.enums.OAuthProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_social_accounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}),
        indexes = @Index(name = "idx_social_user_provider", columnList = "user_id, provider"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSocialAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 191)
    private String providerUserId;

    @Column(name = "provider_email", columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
    private String providerEmail;

    @Column(name = "raw_profile", columnDefinition = "JSON")
    private String rawProfile;

    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;
}
