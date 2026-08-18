package com.example.hotelmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "idx_al_entity", columnList = "entity_type, entity_id, created_at DESC"),
                @Index(name = "idx_al_actor", columnList = "actor_user_id, created_at DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "before_data", columnDefinition = "JSON")
    private String beforeData;

    @Column(name = "after_data", columnDefinition = "JSON")
    private String afterData;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
}
