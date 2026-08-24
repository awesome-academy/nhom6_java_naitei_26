package com.example.hotelmanagement.dto.cancellationpolicy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record CancellationPolicyResponse(
        String code,
        String name,
        String description,
        BigDecimal noShowChargePercent,
        BigDecimal priceAdjustmentPercent,
        Boolean isDefault,
        Boolean isActive,
        List<CancellationPolicyRuleResponse> rules,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public CancellationPolicyResponse(
            String code,
            String name,
            String description,
            BigDecimal noShowChargePercent,
            Boolean isDefault,
            Boolean isActive,
            List<CancellationPolicyRuleResponse> rules,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(
                code,
                name,
                description,
                noShowChargePercent,
                BigDecimal.ZERO,
                isDefault,
                isActive,
                rules,
                createdAt,
                updatedAt
        );
    }
}
