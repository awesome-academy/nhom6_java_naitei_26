package com.example.hotelmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "booking_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSource extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "is_external", nullable = false)
    @Builder.Default
    private Boolean isExternal = false;

    @Column(name = "requires_account", nullable = false)
    @Builder.Default
    private Boolean requiresAccount = false;

    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal commissionPercent = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
