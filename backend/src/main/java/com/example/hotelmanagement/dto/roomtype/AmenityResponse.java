package com.example.hotelmanagement.dto.roomtype;

import com.example.hotelmanagement.entity.enums.AmenityCategory;

public record AmenityResponse(
        String code,
        String name,
        String icon,
        AmenityCategory category,
        Boolean isFilterable,
        Integer sortOrder
) {
}
