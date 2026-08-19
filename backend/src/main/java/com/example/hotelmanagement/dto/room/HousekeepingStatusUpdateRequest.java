package com.example.hotelmanagement.dto.room;

import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import jakarta.validation.constraints.NotNull;

public record HousekeepingStatusUpdateRequest(
        @NotNull HousekeepingStatus status
) {
}
