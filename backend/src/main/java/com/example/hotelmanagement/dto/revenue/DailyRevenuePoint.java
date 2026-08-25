package com.example.hotelmanagement.dto.revenue;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyRevenuePoint(
        LocalDate date,
        BigDecimal revenue,
        BigDecimal otaCommission,
        long bookingCount
) {
}
