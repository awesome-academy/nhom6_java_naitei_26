package com.example.hotelmanagement.dto.roomtype;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record RoomTypeCreateRequest(
        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_]+$")
        String code,

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
        @Min(0) Integer sortOrder,
        Boolean payAtHotelEnabled,
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
        BigDecimal payAtHotelPriceAdjustmentPercent,
        @NotNull @Size(max = 20)
        Set<@NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_]+$") String> onlineCancellationPolicyCodes,

        @NotEmpty @Size(max = 6) List<@Valid RoomTypeBedRequest> beds,

        @NotNull @Size(max = 100)
        Set<@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_]+$") String> amenityCodes
) {
    public RoomTypeCreateRequest(
            String code,
            String name,
            String description,
            Integer maxOccupancy,
            Integer maxAdults,
            Integer maxChildren,
            BigDecimal basePrice,
            String currency,
            BigDecimal extraBedPrice,
            BigDecimal sizeSqm,
            Boolean isActive,
            Integer sortOrder,
            String cancellationPolicyCode,
            List<RoomTypeBedRequest> beds,
            Set<String> amenityCodes
    ) {
        this(
                code,
                name,
                description,
                maxOccupancy,
                maxAdults,
                maxChildren,
                basePrice,
                currency,
                extraBedPrice,
                sizeSqm,
                isActive,
                sortOrder,
                cancellationPolicyCode != null,
                new BigDecimal("10.00"),
                cancellationPolicyCode == null ? Set.of() : Set.of(cancellationPolicyCode),
                beds,
                amenityCodes
        );
    }
}
