package com.example.hotelmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record AppCorsProperties(
    List<String> allowedOrigins
) {
    public AppCorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:3000");
        }
    }
}
