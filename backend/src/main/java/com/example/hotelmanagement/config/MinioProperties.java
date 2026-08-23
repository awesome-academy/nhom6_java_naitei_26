package com.example.hotelmanagement.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String roomImagesBucket,
        @NotNull Duration uploadUrlTtl,
        @NotNull Duration downloadUrlTtl,
        @Positive long maxImageSizeBytes,
        @Min(1) int maxImagesPerRoom,
        @NotBlank String invoicesBucket,
        @NotNull Duration invoicePdfUrlTtl
) {
}
