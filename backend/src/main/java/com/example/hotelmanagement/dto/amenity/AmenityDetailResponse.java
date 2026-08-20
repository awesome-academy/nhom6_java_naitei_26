package com.example.hotelmanagement.dto.amenity;

import com.example.hotelmanagement.entity.enums.AmenityCategory;

import java.time.OffsetDateTime;

public record AmenityDetailResponse(
        String code,
        String name,
        String icon,
        AmenityCategory category,
        Boolean isFilterable,
        Integer sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
