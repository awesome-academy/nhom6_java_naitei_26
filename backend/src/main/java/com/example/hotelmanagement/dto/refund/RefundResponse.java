package com.example.hotelmanagement.dto.refund;

import com.example.hotelmanagement.entity.enums.RefundReason;
import com.example.hotelmanagement.entity.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RefundResponse(
        Long id,
        String bookingPublicId,
        String paymentCode,
        BigDecimal amount,
        RefundReason reason,
        RefundStatus status,
        String policyApplied,
        Long requestedBy,
        Long approvedBy,
        String providerRefundId,
        OffsetDateTime processedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
