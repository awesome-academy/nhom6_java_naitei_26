package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "EmailVerificationRequest", description = "Email verification token payload")
public record EmailVerificationRequest(
    @Schema(description = "One-time email verification token", example = "q2gYgP8xH4w3iL2nV7sA9bC1dE5fG6hJ")
    @NotBlank
    @Size(min = 32, max = 256)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    String token
) {
}
