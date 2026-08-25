package com.example.hotelmanagement.dto.booking;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingDetailResponse(
        BookingResponse booking,
        BigDecimal servicesTotal,
        BigDecimal discountTotal,
        BigDecimal paidAmount,
        BigDecimal refundedAmount,
        String specialRequests,
        OffsetDateTime confirmedAt,
        OffsetDateTime checkedInAt,
        OffsetDateTime checkedOutAt,
        OffsetDateTime cancelledAt,
        String cancellationReason,
        List<BookingStatusHistoryResponse> statusHistory
) {
}
