package com.example.hotelmanagement.dto.revenue;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlyRevenuePoint(
        YearMonth month,
        BigDecimal revenue,
        BigDecimal otaCommission,
        long bookingCount
) {
}
