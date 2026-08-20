package com.example.hotelmanagement.dto.booking;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BookingRoomNightResponse(
        LocalDate stayDate,
        BigDecimal price
) {
}
