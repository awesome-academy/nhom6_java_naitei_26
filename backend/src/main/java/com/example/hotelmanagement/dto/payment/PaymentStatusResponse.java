package com.example.hotelmanagement.dto.payment;

import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentStatusResponse(
        String paymentCode,
        String bookingPublicId,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String provider,
        String failureCode,
        String failureMessage,
        OffsetDateTime expiresAt,
        boolean retryable,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
