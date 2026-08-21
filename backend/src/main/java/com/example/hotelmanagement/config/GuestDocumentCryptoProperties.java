package com.example.hotelmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.guest-document")
public record GuestDocumentCryptoProperties(
        String encryptionKey,
        String lookupHmacKey
) {
}
