package com.example.hotelmanagement.dto.roomtype;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RoomTypeUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 10_000) String description,
        @NotNull @Min(1) @Max(100) Integer maxOccupancy,
        @NotNull @Min(1) @Max(100) Integer maxAdults,
        @Min(0) @Max(100) Integer maxChildren,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal basePrice,
        @Size(min = 3, max = 3) @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal extraBedPrice,
        @DecimalMin(value = "0.01") @Digits(integer = 4, fraction = 2) BigDecimal sizeSqm,
        Boolean isActive,
        @Min(0) Integer sortOrder
) {
}
