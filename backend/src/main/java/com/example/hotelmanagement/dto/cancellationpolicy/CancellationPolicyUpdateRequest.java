package com.example.hotelmanagement.dto.cancellationpolicy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CancellationPolicyUpdateRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @Size(max = 10_000)
        String description,

        @DecimalMin("0.00")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal noShowChargePercent,

        @DecimalMin("0.00")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal priceAdjustmentPercent,

        Boolean isDefault,
        Boolean isActive,

        @NotNull
        @Size(min = 1)
        List<@Valid CancellationPolicyRuleRequest> rules
) {
    public CancellationPolicyUpdateRequest(
            String name,
            String description,
            BigDecimal noShowChargePercent,
            Boolean isDefault,
            Boolean isActive,
            List<CancellationPolicyRuleRequest> rules
    ) {
        this(
                name,
                description,
                noShowChargePercent,
                BigDecimal.ZERO,
                isDefault,
                isActive,
                rules
        );
    }
}
