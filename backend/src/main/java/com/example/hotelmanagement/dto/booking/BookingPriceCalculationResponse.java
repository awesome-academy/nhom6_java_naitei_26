package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.dto.pricing.DailyRateResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BookingPriceCalculationResponse(
        Long roomId,
        Long roomTypeId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long nights,
        Integer adults,
        Integer children,
        List<DailyRateResponse> dailyRates,
        BigDecimal roomsTotal,
        BigDecimal roomTaxPercentSnapshot,
        BigDecimal taxTotal,
        BigDecimal totalAmount,
        String currency
) {
}
