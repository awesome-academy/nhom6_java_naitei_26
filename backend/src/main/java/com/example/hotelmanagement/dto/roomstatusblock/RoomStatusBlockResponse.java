package com.example.hotelmanagement.dto.roomstatusblock;

import com.example.hotelmanagement.entity.enums.RoomBlockType;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record RoomStatusBlockResponse(
        String publicId,
        String roomNumber,
        RoomOperationalStatus operationalStatus,
        RoomBlockType blockType,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
