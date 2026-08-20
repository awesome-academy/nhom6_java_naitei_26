package com.example.hotelmanagement.dto.room;

import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import jakarta.validation.constraints.NotNull;

public record RoomOperationalStatusUpdateRequest(
        @NotNull RoomOperationalStatus status
) {
}
