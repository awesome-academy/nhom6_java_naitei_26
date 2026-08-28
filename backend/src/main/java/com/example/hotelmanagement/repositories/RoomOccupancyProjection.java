package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;

public interface RoomOccupancyProjection {

    String getRoomNumber();

    BookingStatus getBookingStatus();

    BookingRoomStatus getBookingRoomStatus();
}
