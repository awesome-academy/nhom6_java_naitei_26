package com.example.hotelmanagement.dto.amenity;

import com.example.hotelmanagement.entity.enums.AmenityCategory;

public record AmenityFilterOptionResponse(
        String code,
        String name,
        String icon,
        AmenityCategory category,
        Integer sortOrder
) {
}
