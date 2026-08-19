package com.example.hotelmanagement.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "app.hotel")
public record HotelProperties(
        @NotNull ZoneId timeZone
) {
}
