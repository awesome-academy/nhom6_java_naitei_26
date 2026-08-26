package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DashboardStayProjection {

    String getBookingPublicId();

    String getBookingCode();

    String getContactName();

    String getContactPhone();

    String getRoomNumber();

    String getRoomTypeName();

    LocalDate getCheckInDate();

    LocalDate getCheckOutDate();

    BookingStatus getBookingStatus();

    BookingRoomStatus getBookingRoomStatus();

    BigDecimal getTotalAmount();

    BigDecimal getPaidAmount();

    BigDecimal getRefundedAmount();
}
