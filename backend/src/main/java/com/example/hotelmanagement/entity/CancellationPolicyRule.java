package com.example.hotelmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "cancellation_policy_rules",
        uniqueConstraints = @UniqueConstraint(columnNames = {"policy_id", "min_hours_before"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationPolicyRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private CancellationPolicy policy;

    @Column(name = "min_hours_before", nullable = false)
    private Integer minHoursBefore;

    @Column(name = "refund_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal refundPercent;
}
