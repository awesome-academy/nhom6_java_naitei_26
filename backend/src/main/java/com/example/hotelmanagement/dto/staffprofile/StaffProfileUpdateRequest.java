package com.example.hotelmanagement.dto.staffprofile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "StaffProfileUpdateRequest", description = "Partial update payload for a staff profile. Does not change employment status.")
public record StaffProfileUpdateRequest(
        @Schema(nullable = true)
        @Size(max = 80)
        String position,

        @Schema(description = "Blank value clears it.", nullable = true)
        @Size(max = 80)
        String department,

        @Schema(nullable = true)
        @DecimalMin("0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal baseSalary
) {
}
