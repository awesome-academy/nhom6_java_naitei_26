package com.example.hotelmanagement.dto.payment;

import com.example.hotelmanagement.entity.enums.BookingPaymentStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.PaymentMethod;
import com.example.hotelmanagement.entity.enums.PaymentStatus;
import com.example.hotelmanagement.entity.enums.RefundReason;
import com.example.hotelmanagement.entity.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PaymentDetailResponse(
        String paymentCode,
        String bookingPublicId,
        String bookingCode,
        String contactName,
        BookingStatus bookingStatus,
        BookingPaymentStatus bookingPaymentStatus,
        BigDecimal bookingTotalAmount,
        BigDecimal bookingPaidAmount,
        BigDecimal bookingRefundedAmount,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String provider,
        String providerTxnId,
        String providerBankCode,
        BigDecimal refundedAmount,
        OffsetDateTime paidAt,
        OffsetDateTime verifiedAt,
        OffsetDateTime expiresAt,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<RefundSummary> refunds
) {

    public record RefundSummary(
            Long id,
            BigDecimal amount,
            RefundReason reason,
            RefundStatus status,
            Long requestedBy,
            Long approvedBy,
            String providerRefundId,
            OffsetDateTime createdAt,
            OffsetDateTime processedAt
    ) {
    }
}
