package com.example.hotelmanagement.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        @NotNull Duration pollInterval,
        @NotNull Duration retryDelay,
        @NotNull Duration sendingTimeout,
        @Min(1) @Max(10) int maxAttempts,
        @Min(1) @Max(100) int batchSize,
        @NotBlank String provider,
        @NotBlank @Email String fromAddress,
        String fromName,
        @Email String replyTo
) {
}
