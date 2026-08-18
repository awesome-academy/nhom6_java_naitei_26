package com.example.hotelmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(UserRole.UserRoleId.class)
public class UserRole {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRoleId implements Serializable {
        private Long user;
        private Long role;
    }
}
