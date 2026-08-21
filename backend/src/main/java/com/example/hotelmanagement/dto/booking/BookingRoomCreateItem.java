package com.example.hotelmanagement.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

@Schema(name = "BookingRoomCreateItem", description = "A single room stay requested as part of a new booking")
public record BookingRoomCreateItem(
        @NotNull @Positive Long roomId,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @NotNull @Min(1) Integer adults,
        @NotNull @Min(0) Integer children
) {
}
