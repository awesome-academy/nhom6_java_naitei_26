package com.example.hotelmanagement.dto.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyRateResponse(
        LocalDate date,
        BigDecimal price
) {
}
