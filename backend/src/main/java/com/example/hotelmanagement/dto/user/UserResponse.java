package com.example.hotelmanagement.dto.user;

import com.example.hotelmanagement.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Set;

@Schema(name = "UserResponse", description = "User account data")
public record UserResponse(
    @Schema(description = "Public UUID exposed to API clients", example = "a4bd3084-2dd4-4f2b-aaf4-66f29b31a52c")
    String publicId,

    @Schema(description = "User email", example = "guest@example.com")
    String email,

    @Schema(description = "Email verification timestamp", nullable = true)
    OffsetDateTime emailVerifiedAt,

    @Schema(description = "Optional phone number", example = "+84901234567", nullable = true)
    String phone,

    @Schema(description = "User full name", example = "Nguyen Van A")
    String fullName,

    @Schema(description = "Optional avatar URL", nullable = true)
    String avatarUrl,

    @Schema(description = "Current user status", example = "ACTIVE")
    UserStatus status,

    @Schema(description = "Assigned role codes", example = "[\"CUSTOMER\"]")
    Set<String> roles,

    @Schema(description = "Created timestamp")
    OffsetDateTime createdAt,

    @Schema(description = "Updated timestamp")
    OffsetDateTime updatedAt
) {
}
