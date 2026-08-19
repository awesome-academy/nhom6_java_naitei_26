package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest", description = "Refresh token rotation payload")
public record RefreshTokenRequest(
    @Schema(description = "Refresh token returned by login/register/refresh", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank String refreshToken
) {
}
