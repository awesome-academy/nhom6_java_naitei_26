package com.example.hotelmanagement.dto.customerprofile;

import com.example.hotelmanagement.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(name = "CustomerProfileResponse", description = "Customer profile data")
public record CustomerProfileResponse(
    @Schema(description = "Public UUID of the owning user", example = "a4bd3084-2dd4-4f2b-aaf4-66f29b31a52c")
    String userPublicId,

    @Schema(description = "Owner email", example = "guest@example.com")
    String email,

    @Schema(description = "Owner full name", example = "Nguyen Van A")
    String fullName,

    @Schema(description = "Date of birth", nullable = true)
    LocalDate dateOfBirth,

    @Schema(description = "Gender", nullable = true)
    Gender gender,

    @Schema(description = "ISO 3166-1 alpha-2 nationality code", example = "VN", nullable = true)
    String nationality,

    @Schema(description = "Address line", nullable = true)
    String addressLine,

    @Schema(description = "City", nullable = true)
    String city,

    @Schema(description = "Country", nullable = true)
    String country,

    @Schema(description = "Accumulated loyalty points", example = "0")
    Integer loyaltyPoints,

    @Schema(description = "Total completed stays", example = "0")
    Integer totalStays,

    @Schema(description = "Internal notes", nullable = true)
    String notes,

    @Schema(description = "Created timestamp")
    OffsetDateTime createdAt,

    @Schema(description = "Updated timestamp")
    OffsetDateTime updatedAt
) {
}
