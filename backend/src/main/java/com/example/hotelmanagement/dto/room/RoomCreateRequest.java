package com.example.hotelmanagement.dto.room;

import com.example.hotelmanagement.entity.enums.RoomView;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RoomCreateRequest(
        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String roomNumber,

        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_]+$")
        String roomTypeCode,

        RoomView viewType,
        Integer floor,

        @DecimalMin("0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal priceOverride
) {
}
