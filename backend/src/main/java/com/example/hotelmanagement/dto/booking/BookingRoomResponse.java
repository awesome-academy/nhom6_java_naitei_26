package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingPaymentOption;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingRoomResponse(
        Long bookingRoomId,
        String roomNumber,
        String roomTypeCode,
        String roomTypeName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        BookingRoomStatus status,
        Integer guestCount,
        BigDecimal roomSubtotal,
        String cancellationPolicyCode,
        String cancellationPolicyName,
        BookingPaymentOption paymentOption,
        BigDecimal priceAdjustmentPercent,
        OffsetDateTime assignedAt,
        Long assignedByStaffId,
        List<BookingRoomNightResponse> nights
) {
}
