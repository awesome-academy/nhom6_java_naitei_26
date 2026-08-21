package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BookingRoomChangeResponse(
        String bookingPublicId,
        String bookingCode,
        LocalDate moveDate,
        Long previousBookingRoomId,
        String previousRoomNumber,
        BookingRoomStatus previousRoomStatus,
        LocalDate previousCheckInDate,
        LocalDate previousCheckOutDate,
        Long newBookingRoomId,
        String newRoomNumber,
        BookingRoomStatus newRoomStatus,
        LocalDate newCheckInDate,
        LocalDate newCheckOutDate,
        Long movedFromBookingRoomId,
        OffsetDateTime assignedAt,
        Long assignedByStaffId,
        int transferredNightCount
) {
}
