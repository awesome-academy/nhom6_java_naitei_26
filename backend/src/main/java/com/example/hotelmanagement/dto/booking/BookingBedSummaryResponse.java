package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.BedType;

import java.math.BigDecimal;

public record BookingBedSummaryResponse(
        BedType bedType,
        Integer quantity,
        BigDecimal totalAmount
) {
}
