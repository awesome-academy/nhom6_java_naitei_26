package com.example.hotelmanagement.dto.roomtype;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RoomTypeResponse(
        String code,
        String name,
        String slug,
        String description,
        Integer bedCount,
        Integer maxOccupancy,
        Integer maxAdults,
        Integer maxChildren,
        BigDecimal basePrice,
        String currency,
        BigDecimal extraBedPrice,
        BigDecimal sizeSqm,
        Boolean isActive,
        Integer sortOrder,
        List<RoomTypeBedResponse> beds,
        List<AmenityResponse> amenities,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
