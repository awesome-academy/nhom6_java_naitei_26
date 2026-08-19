package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "PasswordResetConfirmRequest", description = "Password reset confirmation payload")
public record PasswordResetConfirmRequest(
    @Schema(description = "One-time password reset token", example = "W8aXyZ7uT6sR5qP4nM3kL2jH1gF0eD9cB")
    @NotBlank
    @Size(min = 32, max = 256)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    String token,

    @Schema(description = "New plain password. Backend stores only the BCrypt hash.", example = "new-secure-password")
    @NotBlank
    @Size(min = 12, max = 64)
    String newPassword
) {
}
