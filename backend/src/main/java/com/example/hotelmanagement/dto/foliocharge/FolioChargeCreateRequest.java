package com.example.hotelmanagement.dto.foliocharge;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FolioChargeCreateRequest(
        @Size(max = 40) String serviceItemCode,
        @Size(max = 200) String description,
        @NotNull
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        BigDecimal quantity,
        @DecimalMin("0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal unitPrice,
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal taxPercent
) {
}
