package com.example.hotelmanagement.dto.refund;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RefundPreviewResponse(
        String bookingPublicId,
        String currency,
        OffsetDateTime asOf,
        boolean hasReceivedPayment,
        BigDecimal estimatedNetRefund,
        String policyApplied
) {
}
