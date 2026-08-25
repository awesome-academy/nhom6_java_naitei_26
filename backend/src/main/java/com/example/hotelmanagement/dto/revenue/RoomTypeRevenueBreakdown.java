package com.example.hotelmanagement.dto.revenue;

import java.math.BigDecimal;

public record RoomTypeRevenueBreakdown(
        String roomTypeCode,
        String roomTypeName,
        BigDecimal revenue,
        long roomNights,
        BigDecimal adr
) {
}
