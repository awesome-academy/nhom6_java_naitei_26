package com.example.hotelmanagement.dto.roomtype;

import com.example.hotelmanagement.entity.enums.BedType;

public record RoomTypeBedResponse(
        BedType bedType,
        Integer quantity
) {
}
