package com.example.hotelmanagement.dto.customerprofile;

import com.example.hotelmanagement.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(name = "CustomerProfileResponse", description = "Customer profile data")
public record CustomerProfileResponse(
    @Schema(description = "Public UUID of the owning user", example = "a4bd3084-2dd4-4f2b-aaf4-66f29b31a52c")
    String publicId,

    @Schema(description = "Owner email", example = "guest@example.com")
    String email,

    @Schema(description = "Owner phone number", example = "0901234567", nullable = true)
    String phone,

    @Schema(description = "Owner full name", example = "Nguyen Van A")
    String fullName,

    @Schema(description = "Date of birth", example = "1990-01-15", nullable = true)
    LocalDate dateOfBirth,

    @Schema(description = "Gender", example = "MALE", nullable = true)
    Gender gender,

    @Schema(description = "ISO 3166-1 alpha-2 nationality code", example = "VN", nullable = true)
    String nationality,

    @Schema(description = "Province/Thành phố", example = "Hà Nội", nullable = true)
    String province,

    @Schema(description = "Detailed address line", example = "123 Nguyễn Trãi", nullable = true)
    String addressLine,

    @Schema(description = "Country code", example = "VN", nullable = true)
    String country,

    @Schema(description = "Avatar URL", example = "https://example.com/avatar.jpg", nullable = true)
    String avatarUrl,

    @Schema(description = "Whether email is verified", example = "true")
    boolean emailVerified,

    @Schema(description = "When the user registered", example = "2026-08-22T14:30:00+07:00")
    OffsetDateTime joinedAt,

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
