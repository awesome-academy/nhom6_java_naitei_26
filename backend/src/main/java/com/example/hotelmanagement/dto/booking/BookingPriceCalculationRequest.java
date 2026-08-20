package com.example.hotelmanagement.dto.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BookingPriceCalculationRequest(
        @NotNull @Positive Long roomId,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @NotNull @Min(1) Integer adults,
        @NotNull @Min(0) Integer children
) {
}
