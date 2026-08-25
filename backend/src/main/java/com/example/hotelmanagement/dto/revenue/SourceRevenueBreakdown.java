package com.example.hotelmanagement.dto.revenue;

import java.math.BigDecimal;

public record SourceRevenueBreakdown(
        String sourceCode,
        String sourceName,
        BigDecimal revenue,
        BigDecimal otaCommission,
        long bookingCount
) {
}
