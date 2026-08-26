package com.example.hotelmanagement.dto.room;

import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.entity.enums.RoomView;

import java.math.BigDecimal;

/**
 * Room info for assignment modal (filtered by availability and housekeeping status).
 */
public record AvailableRoomForAssignmentResponse(
        Long id,
        String roomNumber,
        String roomTypeCode,
        String roomTypeName,
        Integer floor,
        RoomView viewType,
        HousekeepingStatus housekeepingStatus,
        RoomOperationalStatus operationalStatus,
        BigDecimal priceOverride,
        BigDecimal effectivePrice
) {}
