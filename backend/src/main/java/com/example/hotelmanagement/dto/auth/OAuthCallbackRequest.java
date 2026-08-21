package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "OAuthCallbackRequest", description = "OAuth callback payload from frontend")
public record OAuthCallbackRequest(
    @Schema(description = "Authorization code from OAuth provider", example = "4/0Adeu5...")
    @NotBlank
    String code,

    @Schema(description = "State parameter for CSRF protection", example = "random-state-value")
    String state,

    @Schema(description = "Error from OAuth provider if any", example = "access_denied")
    String error,

    @Schema(description = "Error description from OAuth provider", example = "User denied the request")
    String errorDescription
) {
}
