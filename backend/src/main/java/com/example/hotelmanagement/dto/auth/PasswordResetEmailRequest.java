package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "PasswordResetEmailRequest", description = "Password reset request payload")
public record PasswordResetEmailRequest(
    @Schema(description = "Account email that should receive the reset link", example = "guest@example.com")
    @NotBlank
    @Email
    @Size(max = 255)
    String email
) {
}
