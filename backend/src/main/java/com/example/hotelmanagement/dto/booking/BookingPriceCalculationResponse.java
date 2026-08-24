package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.dto.pricing.DailyRateResponse;
import com.example.hotelmanagement.entity.enums.BookingPaymentOption;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BookingPriceCalculationResponse(
        Long roomId,
        Long roomTypeId,
        String roomTypeCode,
        BookingPaymentOption paymentOption,
        String cancellationPolicyCode,
        String cancellationPolicyName,
        BigDecimal priceAdjustmentPercent,
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
    public BookingPriceCalculationResponse(
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
        this(
                roomId,
                roomTypeId,
                String.valueOf(roomTypeId),
                BookingPaymentOption.ONLINE,
                "FLEXIBLE",
                "Flexible",
                BigDecimal.ZERO,
                checkInDate,
                checkOutDate,
                nights,
                adults,
                children,
                dailyRates,
                roomsTotal,
                roomTaxPercentSnapshot,
                taxTotal,
                totalAmount,
                currency
        );
    }
}
