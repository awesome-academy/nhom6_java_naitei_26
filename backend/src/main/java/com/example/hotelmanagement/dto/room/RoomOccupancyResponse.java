package com.example.hotelmanagement.dto.room;

public record RoomOccupancyResponse(
        String roomNumber,
        RoomBookingStatus bookingStatus
) {
}
