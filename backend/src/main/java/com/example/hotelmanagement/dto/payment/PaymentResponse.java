package com.example.hotelmanagement.dto.payment;

import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PaymentResponse(
        String paymentCode,
        String bookingPublicId,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String provider,
        String paymentUrl,
        String deeplink,
        String qrCodeValue,
        List<PaymentGatewayFormField> checkoutFields,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
}
