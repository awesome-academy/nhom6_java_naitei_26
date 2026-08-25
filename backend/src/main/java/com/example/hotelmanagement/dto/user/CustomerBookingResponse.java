package com.example.hotelmanagement.dto.user;

import com.example.hotelmanagement.entity.enums.BookingPaymentStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerBookingResponse(
        String bookingCode,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int nights,
        int rooms,
        int guests,
        BigDecimal totalAmount,
        String currency,
        BookingStatus status,
        BookingPaymentStatus paymentStatus
) {
}
