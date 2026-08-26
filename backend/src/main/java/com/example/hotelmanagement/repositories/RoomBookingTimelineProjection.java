package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;

import java.time.LocalDate;

public interface RoomBookingTimelineProjection {

    Long getRoomId();

    String getBookingPublicId();

    String getBookingCode();

    BookingStatus getBookingStatus();

    BookingRoomStatus getBookingRoomStatus();

    LocalDate getStartDate();

    LocalDate getEndDate();
}
