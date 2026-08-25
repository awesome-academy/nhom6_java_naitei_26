package com.example.hotelmanagement.dto.revenue;

import java.math.BigDecimal;

public record OccupancyMetrics(
        BigDecimal adr,
        BigDecimal occupancyRatePercent,
        BigDecimal revPar,
        long occupiedRoomNights,
        long availableRoomNights
) {
}
