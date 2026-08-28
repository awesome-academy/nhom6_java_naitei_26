package com.example.hotelmanagement.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", description = "Customer registration payload")
public record RegisterRequest(
    @Schema(description = "Unique email used for login", example = "guest@example.com")
    @NotBlank
    @Email
    @Size(max = 255)
    String email,

    @Schema(description = "Plain password. Backend stores only the BCrypt hash.", example = "very-secure-password")
    @NotBlank
    @Size(min = 12, max = 64)
    String password,

    @Schema(description = "Display name", example = "Nguyen Van A")
    @NotBlank
    @Size(max = 150)
    String fullName,

    @Schema(description = "Optional phone number", example = "+84901234567")
    @Size(max = 20)
    @Pattern(regexp = "^(?:\\s*|(?:[+() .-]*[0-9]){10,15}[+() .-]*)$")
    String phone
) {
}
