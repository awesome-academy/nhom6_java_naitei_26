package com.example.hotelmanagement.dto.shift;

import java.time.LocalTime;
import java.time.OffsetDateTime;

public record ShiftResponse(
        String code,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        Boolean crossesMidnight,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
