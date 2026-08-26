package com.example.hotelmanagement.services;

public interface EmailTransport {

    DeliveryResult send(DispatchMessage message);

    String getProviderCode();

    record DispatchMessage(
            Long id,
            String templateCode,
            String toEmail,
            String subject,
            String bodyHtml,
            String bodyText
    ) {
    }

    record DeliveryResult(String provider, String providerMessageId) {
    }
}
