package com.example.hotelmanagement.dto.pricing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record RateOverrideUpdateRequest(
        @NotNull @Positive Long roomTypeId,
        @NotBlank @Size(max = 120) String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal price,
        @Size(min = 1, max = 7) Set<@NotNull @Min(1) @Max(7) Integer> weekdays,
        @NotNull Integer priority
) {
}
