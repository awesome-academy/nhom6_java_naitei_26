package com.example.hotelmanagement.dto.amenity;

import com.example.hotelmanagement.entity.enums.AmenityCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AmenityUpdateRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @Size(max = 60)
        String icon,

        @NotNull
        AmenityCategory category,

        @NotNull
        Boolean isFilterable,

        @NotNull
        @PositiveOrZero
        Integer sortOrder
) {
}
