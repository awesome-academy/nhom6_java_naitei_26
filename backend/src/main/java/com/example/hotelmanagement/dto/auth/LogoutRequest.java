package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LogoutRequest", description = "Logout payload")
public record LogoutRequest(
    @Schema(description = "Refresh token to revoke", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank String refreshToken
) {
}
