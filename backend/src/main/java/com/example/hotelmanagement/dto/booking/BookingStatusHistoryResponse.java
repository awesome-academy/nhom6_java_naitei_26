package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.ActorType;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.StatusChangeSource;

import java.time.OffsetDateTime;

public record BookingStatusHistoryResponse(
        BookingStatus fromStatus,
        BookingStatus toStatus,
        ActorType actorType,
        StatusChangeSource source,
        String reason,
        OffsetDateTime createdAt
) {
}
