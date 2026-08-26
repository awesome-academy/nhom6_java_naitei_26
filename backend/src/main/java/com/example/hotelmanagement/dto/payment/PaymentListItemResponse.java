package com.example.hotelmanagement.dto.payment;

import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentListItemResponse(
        String paymentCode,
        String bookingPublicId,
        String bookingCode,
        String contactName,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String provider,
        String providerTxnId,
        BigDecimal refundedAmount,
        OffsetDateTime paidAt,
        OffsetDateTime verifiedAt,
        OffsetDateTime createdAt
) {
}
