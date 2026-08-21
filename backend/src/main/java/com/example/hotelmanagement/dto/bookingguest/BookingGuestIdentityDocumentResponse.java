package com.example.hotelmanagement.dto.bookingguest;

import com.example.hotelmanagement.entity.enums.IdDocumentType;

import java.time.OffsetDateTime;

public record BookingGuestIdentityDocumentResponse(
        Long guestId,
        String bookingPublicId,
        IdDocumentType idDocumentType,
        String idDocumentNumber,
        OffsetDateTime accessedAt
) {
}
