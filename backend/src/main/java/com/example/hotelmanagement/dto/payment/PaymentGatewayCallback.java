package com.example.hotelmanagement.dto.payment;

import java.math.BigDecimal;

public record PaymentGatewayCallback(
        String provider,
        String providerEventId,
        String paymentCode,
        String providerTransactionId,
        BigDecimal amount,
        Integer resultCode,
        String message,
        String paymentType,
        boolean signatureValid
) {

    public boolean isSuccessful() {
        return Integer.valueOf(0).equals(resultCode);
    }

    public boolean isAuthorized() {
        return Integer.valueOf(9000).equals(resultCode);
    }
}
