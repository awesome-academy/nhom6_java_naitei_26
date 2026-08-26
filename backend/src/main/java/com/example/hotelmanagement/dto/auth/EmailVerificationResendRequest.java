package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "EmailVerificationResendRequest", description = "Request a new email verification link")
public record EmailVerificationResendRequest(
    @Schema(description = "Email address associated with the pending account", example = "guest@example.com")
    @NotBlank
    @Email
    @Size(max = 255)
    String email
) {
}
