package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.BookingStatus;

import java.time.OffsetDateTime;

/**
 * Response after confirming a booking.
 */
public record BookingConfirmResponse(
        String publicId,
        String bookingCode,
        BookingStatus status,
        OffsetDateTime confirmedAt,
        OffsetDateTime checkedInAt
) {}
