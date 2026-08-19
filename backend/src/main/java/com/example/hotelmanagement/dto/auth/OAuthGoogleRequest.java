package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "OAuthGoogleRequest", description = "Development stub payload for Google OAuth login")
public record OAuthGoogleRequest(
    @Schema(description = "Google subject/user id from OAuth provider", example = "google-user-123")
    @NotBlank
    @Size(max = 191)
    String providerUserId,

    @Schema(description = "Email returned by Google", example = "guest@gmail.com")
    @NotBlank
    @Email
    @Size(max = 255)
    String email,

    @Schema(description = "Name returned by Google", example = "Nguyen Van A")
    @NotBlank
    @Size(max = 150)
    String fullName
) {
}
