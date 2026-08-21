package com.example.hotelmanagement.dto.roomtype;

public record RoomTypeStatsResponse(
        long total,
        long active,
        long deactivated
) {
}
