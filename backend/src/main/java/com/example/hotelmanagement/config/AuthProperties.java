package com.example.hotelmanagement.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    @Min(1) int maxFailedLoginAttempts,
    @NotNull Duration lockDuration,
    @NotNull Duration emailVerificationTokenTtl,
    @NotNull Duration passwordResetTokenTtl,
    @NotBlank String frontendVerifyUrl,
    @NotBlank String frontendResetUrl
) {
}
