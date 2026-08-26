package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.BookingPaymentStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Simplified booking info for list display (Staff view).
 */
public record BookingListItemResponse(
        String publicId,
        String bookingCode,
        BookingStatus status,
        BookingPaymentStatus paymentStatus,
        String sourceCode,
        String sourceName,
        String contactName,
        String contactEmail,
        String contactPhone,
        Integer adults,
        Integer children,
        BigDecimal totalAmount,
        String currency,
        OffsetDateTime holdExpiresAt,
        OffsetDateTime createdAt,
        List<BookingRoomSummary> rooms,
        BookingDatesSummary dates,
        boolean allRoomsAssigned
) {

    /**
     * Summary of booking dates across all rooms.
     */
    public record BookingDatesSummary(
            LocalDate earliestCheckIn,
            LocalDate latestCheckOut,
            int totalNights
    ) {}

    /**
     * Simplified room info for list display.
     */
    public record BookingRoomSummary(
            Long id,
            String roomNumber,
            String roomTypeCode,
            String roomTypeName,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int nights,
            BigDecimal roomSubtotal,
            String status
    ) {}
}
