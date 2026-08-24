package com.example.hotelmanagement.dto.payment;

import java.util.Locale;
import java.util.Map;

public record PaymentGatewayCallbackRequest(String rawPayload, Map<String, String> headers) {

    public String headerValue(String headerName) {
        if (headers == null || headerName == null) {
            return null;
        }
        String normalizedHeaderName = headerName.toLowerCase(Locale.ROOT);
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null
                        && entry.getKey().toLowerCase(Locale.ROOT).equals(normalizedHeaderName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
