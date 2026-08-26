package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.BookingPaymentStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingResponse(
        String publicId,
        String bookingCode,
        BookingStatus status,
        BookingPaymentStatus paymentStatus,
        String sourceCode,
        BigDecimal sourceCommissionPercentSnapshot,
        String contactName,
        String contactEmail,
        String contactPhone,
        Integer adults,
        Integer children,
        BigDecimal roomsTotal,
        BigDecimal taxTotal,
        BigDecimal roomTaxPercentSnapshot,
        BigDecimal totalAmount,
        String currency,
        OffsetDateTime holdExpiresAt,
        List<BookingBedSummaryResponse> bedSummaries,
        List<BookingRoomResponse> rooms,
        OffsetDateTime createdAt
) {
}
