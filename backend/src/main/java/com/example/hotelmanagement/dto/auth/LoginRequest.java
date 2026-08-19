package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "Email/password login payload")
public record LoginRequest(
    @Schema(description = "User email", example = "guest@example.com")
    @NotBlank
    @Email
    @Size(max = 255)
    String email,

    @Schema(description = "Plain password. Must be 12-64 characters.", example = "very-secure-password")
    @NotBlank
    @Size(min = 12, max = 64)
    String password
) {
}
