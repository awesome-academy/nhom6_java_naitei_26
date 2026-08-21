package com.example.hotelmanagement.dto.bookingguest;

import com.example.hotelmanagement.entity.enums.IdDocumentType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BookingGuestResponse(
        Long id,
        String bookingPublicId,
        Long bookingRoomId,
        String roomNumber,
        String fullName,
        String nationality,
        IdDocumentType idDocumentType,
        boolean hasIdDocument,
        LocalDate dateOfBirth,
        OffsetDateTime createdAt
) {
}
