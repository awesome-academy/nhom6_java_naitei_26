package com.example.hotelmanagement.dto.customerprofile;

import com.example.hotelmanagement.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "CustomerProfileUpdateRequest", description = "Partial update payload for the current user's customer profile")
public record CustomerProfileUpdateRequest(
    @Schema(description = "Date of birth", nullable = true)
    @Past
    LocalDate dateOfBirth,

    @Schema(description = "Gender", nullable = true)
    Gender gender,

    @Schema(description = "ISO 3166-1 alpha-2 nationality code. Blank value clears it.", example = "VN", nullable = true)
    @Pattern(regexp = "^[A-Za-z]{2}$|^$")
    String nationality,

    @Schema(description = "Address line. Blank value clears it.", nullable = true)
    @Size(max = 255)
    String addressLine,

    @Schema(description = "City. Blank value clears it.", nullable = true)
    @Size(max = 100)
    String city,

    @Schema(description = "Country. Blank value clears it.", nullable = true)
    @Size(max = 100)
    String country,

    @Schema(description = "Internal notes. Blank value clears it.", nullable = true)
    @Size(max = 2000)
    String notes
) {
}
