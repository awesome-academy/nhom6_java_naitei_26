package com.example.hotelmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "room_type_cancellation_policies",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_room_type_cancel_policy",
                        columnNames = {"room_type_id", "cancellation_policy_id"}
                )
        },
        indexes = {
                @Index(name = "idx_rt_cancel_policy_policy", columnList = "cancellation_policy_id"),
                @Index(name = "idx_rt_cancel_policy_active", columnList = "room_type_id, is_active, sort_order")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeCancellationPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancellation_policy_id", nullable = false)
    private CancellationPolicy cancellationPolicy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
