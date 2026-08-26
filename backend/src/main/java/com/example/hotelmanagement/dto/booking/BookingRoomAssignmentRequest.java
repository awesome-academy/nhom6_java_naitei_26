package com.example.hotelmanagement.dto.booking;

import jakarta.validation.constraints.NotNull;

/**
 * Request to assign a room to a booking room.
 */
public record BookingRoomAssignmentRequest(
        @NotNull(message = "Room ID is required")
        Long roomId
) {}
