package com.example.hotelmanagement.dto.payment;

import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
        String paymentCode,
        String bookingPublicId,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
}
