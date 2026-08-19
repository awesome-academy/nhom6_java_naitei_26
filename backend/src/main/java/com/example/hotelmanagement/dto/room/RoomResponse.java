package com.example.hotelmanagement.dto.room;

import com.example.hotelmanagement.dto.roomimage.RoomImageResponse;
import com.example.hotelmanagement.dto.roomtype.AmenityResponse;
import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.entity.enums.RoomView;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RoomResponse(
        String roomNumber,
        String roomTypeCode,
        String roomTypeName,
        RoomView viewType,
        Integer floor,
        RoomOperationalStatus operationalStatus,
        HousekeepingStatus housekeepingStatus,
        BigDecimal priceOverride,
        Boolean isActive,
        List<AmenityResponse> amenities,
        List<RoomImageResponse> images,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
