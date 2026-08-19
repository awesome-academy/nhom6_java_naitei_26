package com.example.hotelmanagement.dto.auth;

import com.example.hotelmanagement.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Set;

@Schema(name = "AuthResponse", description = "JWT token pair and authenticated user summary")
public record AuthResponse(
    @Schema(description = "HTTP authorization token type", example = "Bearer")
    String tokenType,

    @Schema(description = "JWT access token used in Authorization Bearer header", example = "eyJhbGciOiJIUzI1NiJ9...")
    String accessToken,

    @Schema(description = "Access token TTL in seconds", example = "3600")
    long accessTokenExpiresInSeconds,

    @Schema(description = "JWT refresh token used to rotate token pair", example = "eyJhbGciOiJIUzI1NiJ9...")
    String refreshToken,

    @Schema(description = "Refresh token TTL in seconds", example = "604800")
    long refreshTokenExpiresInSeconds,

    UserSummary user
) {

    @Schema(name = "AuthUserSummary", description = "Authenticated user data embedded in auth response")
    public record UserSummary(
        @Schema(description = "Public UUID exposed to API clients", example = "a4bd3084-2dd4-4f2b-aaf4-66f29b31a52c")
        String publicId,

        @Schema(description = "User email", example = "guest@example.com")
        String email,

        @Schema(description = "User full name", example = "Nguyen Van A")
        String fullName,

        @Schema(description = "Current user status", example = "PENDING_VERIFICATION")
        UserStatus status,

        @Schema(description = "Email verification timestamp. Null before BE-2.2 verification flow.", nullable = true)
        OffsetDateTime emailVerifiedAt,

        @Schema(description = "Role codes assigned to the user", example = "[\"CUSTOMER\"]")
        Set<String> roles,

        @Schema(description = "Permission codes derived from roles", example = "[\"booking:create\",\"booking:read_own\"]")
        Set<String> permissions
    ) {
    }
}
