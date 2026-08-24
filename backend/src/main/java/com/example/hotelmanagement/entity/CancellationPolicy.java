package com.example.hotelmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cancellation_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationPolicy extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "no_show_charge_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal noShowChargePercent = new BigDecimal("100.00");

    @Column(name = "price_adjustment_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal priceAdjustmentPercent = BigDecimal.ZERO;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<CancellationPolicyRule> rules = new HashSet<>();
}
