package com.example.hotelmanagement.dto.room;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.RoomBlockType;

import java.time.LocalDate;

public record RoomTimelineEventResponse(
        RoomTimelineEventType type,
        LocalDate startDate,
        LocalDate endDate,
        String label,
        String bookingPublicId,
        String bookingCode,
        BookingStatus bookingStatus,
        BookingRoomStatus bookingRoomStatus,
        RoomBlockType blockType,
        String reason
) {
}
