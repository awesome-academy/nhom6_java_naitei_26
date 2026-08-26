package com.example.hotelmanagement.dto.room;

import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.entity.enums.RoomView;

import java.util.List;

public record RoomBookingMapResponse(
        Long roomId,
        String roomNumber,
        String roomTypeCode,
        String roomTypeName,
        RoomView viewType,
        Integer floor,
        RoomOperationalStatus operationalStatus,
        HousekeepingStatus housekeepingStatus,
        Integer maxOccupancy,
        boolean selectable,
        String unavailableReason,
        List<RoomTimelineEventResponse> timeline
) {
}
