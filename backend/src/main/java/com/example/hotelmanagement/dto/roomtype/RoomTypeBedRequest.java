package com.example.hotelmanagement.dto.roomtype;

import com.example.hotelmanagement.entity.enums.BedType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RoomTypeBedRequest(
        @NotNull BedType bedType,
        @NotNull @Min(1) @Max(10) Integer quantity
) {
}
