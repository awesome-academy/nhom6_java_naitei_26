package com.example.hotelmanagement.dto.booking;

import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;

import java.time.OffsetDateTime;

public record BookingRoomAssignmentResponse(
        String bookingPublicId,
        String bookingCode,
        BookingStatus bookingStatus,
        Long bookingRoomId,
        String roomNumber,
        BookingRoomStatus roomStatus,
        OffsetDateTime assignedAt,
        Long assignedByStaffId,
        String assignedByEmployeeCode
) {
}
