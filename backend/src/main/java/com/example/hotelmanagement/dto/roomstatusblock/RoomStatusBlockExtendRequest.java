package com.example.hotelmanagement.dto.roomstatusblock;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RoomStatusBlockExtendRequest(
        @NotNull LocalDate newEndDate
) {
}
