package com.example.hotelmanagement.dto.room;

import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;

public record RoomOperationalStatusResponse(
        String roomNumber,
        RoomOperationalStatus operationalStatus
) {
}
